package name.abuchen.portfolio.ui.export;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;

import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonWriter;

import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.money.CurrencyConverter;
import name.abuchen.portfolio.money.Money;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.snapshot.AccountSnapshot;
import name.abuchen.portfolio.snapshot.ClientSnapshot;
import name.abuchen.portfolio.snapshot.PortfolioSnapshot;
import name.abuchen.portfolio.snapshot.SecurityPosition;
import name.abuchen.portfolio.ui.views.holdings.HoldingsPieChartSWT;

public final class HoldingsImageExporter
{
    private HoldingsImageExporter()
    {
    }

    @SuppressWarnings("nls")
    public static void exportDailyHoldingsToJSON(Client client, CurrencyConverter converter, LocalDate from,
                    LocalDate to, File outputDir) throws IOException
    {
        if (!outputDir.exists() && !outputDir.mkdirs())
            throw new IOException("Cannot create output directory: " + outputDir);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        File jsonFile = new File(outputDir, "holdings_all.json");

        try (Writer writer = new FileWriter(jsonFile, StandardCharsets.UTF_8);
                        JsonWriter jsonWriter = new JsonWriter(writer))
        {
            jsonWriter.setIndent("  ");
            jsonWriter.beginArray();

            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1))
            {
                ClientSnapshot snapshot = ClientSnapshot.create(client, converter, d, d.toString());

                jsonWriter.beginObject();

                // Date
                jsonWriter.name("date").value(d.toString());

                // Total value in current currency
                Money totalValue = snapshot.getMonetaryAssets();
                jsonWriter.name("totalValue").value(totalValue.getAmount() / Values.Amount.divider());

                // Accounts
                jsonWriter.name("accounts");
                jsonWriter.beginArray();
                for (AccountSnapshot account : snapshot.getAccounts())
                {
                    Money accountValue = account.getFunds();
                    long amount = accountValue.getAmount();
                    if (amount < 1)
                        continue;
                    jsonWriter.beginObject();
                    jsonWriter.name("name").value(account.getAccount().getName());
                    jsonWriter.name("value").value(amount / Values.Amount.divider());
                    jsonWriter.endObject();
                }
                jsonWriter.endArray();

                // Securities
                jsonWriter.name("securities");
                jsonWriter.beginArray();
                for (PortfolioSnapshot portfolio : snapshot.getPortfolios())
                {
                    for (SecurityPosition position : portfolio.getPositions())
                    {
                        if (position.getSecurity() != null)
                        {
                            Money positionValue = position.calculateValue().with(converter.at(d));
                            jsonWriter.beginObject();
                            jsonWriter.name("name").value(position.getSecurity().getName());
                            jsonWriter.name("isin").value(position.getSecurity().getIsin());
                            jsonWriter.name("shares").value(position.getShares() / Values.Share.divider());
                            jsonWriter.name("value").value(positionValue.getAmount() / Values.Amount.divider());
                            jsonWriter.name("portfolio").value(portfolio.getPortfolio().getName());
                            jsonWriter.endObject();
                        }
                    }
                }
                jsonWriter.endArray();

                jsonWriter.endObject();
            }

            jsonWriter.endArray();
        }
    }

    @SuppressWarnings("nls")
    public static void exportDailyHoldingsPngs(Client client, CurrencyConverter converter, LocalDate from, LocalDate to,
                    File outputDir) throws IOException
    {
        if (!outputDir.exists() && !outputDir.mkdirs())
            throw new IOException("Cannot create output directory: " + outputDir);

        Display display = Display.getDefault();

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT); //$NON-NLS-1$

        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1))
        {
            ClientSnapshot snapshot = ClientSnapshot.create(client, converter, d, d.toString());

            HoldingsPieChartSWT pie = new HoldingsPieChartSWT(snapshot, null);

            Shell shell = new Shell(display);
            shell.setLayout(new FillLayout());
            shell.setSize(1920, 1080);
            pie.createControl(shell);
            shell.layout(true, true);

            // Force the shell to render by opening and processing events
            shell.open();
            while (display.readAndDispatch())
            {
                // Process all pending events to ensure the shell is fully
                // rendered
            }

            Image swtImage = new Image(display, 1920, 1080);
            GC gc = new GC(swtImage);
            shell.print(gc);
            while (display.readAndDispatch())
            {
                // Process all pending events to ensure the shell is fully
                // rendered
            }
            gc.drawOval(0, 0, 100, 100);
            gc.dispose();

            shell.close();
            shell.dispose();

            BufferedImage buffered = convertToBufferedImage(swtImage.getImageData());

            Money total = snapshot.getMonetaryAssets();
            File out = new File(outputDir, "holdings_" + fmt.format(d) + ".png");
            writePngWithText(buffered, out, "PortfolioValue", total.toString());
            swtImage.dispose();
        }
    }

    private static BufferedImage convertToBufferedImage(ImageData data)
    {
        BufferedImage buffered = new BufferedImage(data.width, data.height, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new int[data.width * data.height];
        data.getPixels(0, 0, pixels.length, pixels, 0);
        buffered.setRGB(0, 0, data.width, data.height, pixels, 0, data.width);
        return buffered;
    }

    @SuppressWarnings("nls")
    // $NON-NLS-1$
    private static void writePngWithText(BufferedImage img, File file, String key, String value) throws IOException
    {
        var writer = ImageIO.getImageWritersByFormatName("png").next();
        var ios = ImageIO.createImageOutputStream(file);
        writer.setOutput(ios);

        IIOMetadata meta = writer.getDefaultImageMetadata(new ImageTypeSpecifier(img), null);

        String nativeFormat = meta.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(nativeFormat);
        IIOMetadataNode textNode = new IIOMetadataNode("tEXtEntry");
        textNode.setAttribute("keyword", key);
        textNode.setAttribute("value", value);

        IIOMetadataNode text = getOrCreateChild(root, "tEXt");
        text.appendChild(textNode);
        meta.setFromTree(nativeFormat, root);

        writer.write(null, new IIOImage(img, null, meta), null);
        ios.close();
        writer.dispose();
    }

    private static IIOMetadataNode getOrCreateChild(IIOMetadataNode root, String name)
    {
        for (int i = 0; i < root.getLength(); i++)
        {
            var n = root.item(i);
            if (name.equals(n.getNodeName()))
                return (IIOMetadataNode) n;
        }
        IIOMetadataNode child = new IIOMetadataNode(name);
        root.appendChild(child);
        return child;
    }
}
