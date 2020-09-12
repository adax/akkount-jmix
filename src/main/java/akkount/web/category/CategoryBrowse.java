package akkount.web.category;

import akkount.entity.Category;
import com.haulmont.cuba.gui.screen.LoadDataBeforeShow;
import io.jmix.ui.screen.*;

@UiController("akk_Category.lookup")
@UiDescriptor("category-browse.xml")
@LookupComponent("categoryTable")
@LoadDataBeforeShow
public class CategoryBrowse extends StandardLookup<Category> {
}