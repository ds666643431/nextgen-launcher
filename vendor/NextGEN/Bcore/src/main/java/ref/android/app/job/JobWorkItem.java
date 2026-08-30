package ref.android.app.job;

import android.content.Intent;

import menu.nextgen.reflect.annotation.BClassName;
import menu.nextgen.reflect.annotation.BConstructor;
import menu.nextgen.reflect.annotation.BField;
import menu.nextgen.reflect.annotation.BMethod;

@BClassName("android.app.job.JobWorkItem")
public interface JobWorkItem {
    @BConstructor
    JobWorkItem _new(Intent Intent0);

    @BField
    int mDeliveryCount();

    @BField
    Object mGrants();

    @BField
    int mWorkId();

    @BMethod
    Intent getIntent();
}
