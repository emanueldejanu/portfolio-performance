package name.abuchen.portfolio.ui.commands;

import jakarta.inject.Named;

import org.eclipse.e4.core.di.annotations.CanExecute;
import org.eclipse.e4.core.di.annotations.Execute;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.e4.ui.services.IServiceConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;

import name.abuchen.portfolio.ui.handlers.MenuHelper;

public class HelloHandler
{
    @CanExecute
    boolean isVisible(@Named(IServiceConstants.ACTIVE_PART) MPart part)
    {
        return MenuHelper.isClientPartActive(part);
    }

    @SuppressWarnings("nls")
    @Execute
    public void execute(@Named(IServiceConstants.ACTIVE_PART) MPart part,
                    @Named(IServiceConstants.ACTIVE_SHELL) Shell shell)
    {
        MenuHelper.getActiveClientInput(part, false).ifPresent(clientInput -> {
            var client = clientInput.getClient();
            java.time.LocalDate from = java.time.LocalDate.of(2022, 7, 1);
            java.time.LocalDate to = java.time.LocalDate.now();

            java.io.File outDir = new java.io.File("C:\\temp\\holdings-images");

            var converter = new name.abuchen.portfolio.money.CurrencyConverterImpl(
                            clientInput.getExchangeRateProviderFacory(), client.getBaseCurrency());

            try
            {
                name.abuchen.portfolio.ui.export.HoldingsImageExporter.exportDailyHoldingsToJSON(client, converter,
                                from, to, outDir);

                // MessageDialog.openInformation(shell, "Info", "Hello");
            }
            catch (java.io.IOException e)
            {
                MessageDialog.openError(shell, "Error", "Failed to export holdings images: " + e.getMessage());
            }
        });
    }
}
