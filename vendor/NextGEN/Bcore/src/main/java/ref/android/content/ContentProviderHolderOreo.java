package ref.android.content;

import android.content.pm.ProviderInfo;
import android.os.IInterface;

import menu.nextgen.reflect.annotation.BClassName;
import menu.nextgen.reflect.annotation.BField;

@BClassName("android.app.ContentProviderHolder")
public interface ContentProviderHolderOreo {
    @BField
    ProviderInfo info();

    @BField
    boolean noReleaseNeeded();

    @BField
    IInterface provider();
}
