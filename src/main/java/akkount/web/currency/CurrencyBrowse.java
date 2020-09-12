package akkount.web.currency;

import akkount.entity.Currency;
import com.haulmont.cuba.gui.screen.LoadDataBeforeShow;
import io.jmix.ui.screen.*;

@UiController("akk_Currency.lookup")
@UiDescriptor("currency-browse.xml")
@LookupComponent("currencyTable")
@LoadDataBeforeShow
public class CurrencyBrowse extends StandardLookup<Currency> {
}