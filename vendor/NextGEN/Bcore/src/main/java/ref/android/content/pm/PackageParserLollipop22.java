package ref.android.content.pm;

import android.content.pm.PackageParser.Package;

import java.io.File;

import menu.nextgen.reflect.annotation.BClassName;
import menu.nextgen.reflect.annotation.BConstructor;
import menu.nextgen.reflect.annotation.BMethod;

@BClassName("android.content.pm.PackageParser")
public interface PackageParserLollipop22 {
    @BConstructor
    android.content.pm.PackageParser _new();
















    @BMethod
    void collectCertificates(Package p, int flags);

    @BMethod
    Package parsePackage(File File0, int int1);
}
