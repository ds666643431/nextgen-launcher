package menu.nextgen.launcher.utils.compat;

import java.lang.reflect.Method;
import java.util.List;

import ref.android.content.pm.BRParceledListSlice;

public class ParceledListSliceCompat {

	public static boolean isReturnParceledListSlice(Method method) {
		return method != null && method.getReturnType() == BRParceledListSlice.getRealClass();
	}

	public static boolean isParceledListSlice(Object obj) {
		return obj != null && obj.getClass() == BRParceledListSlice.getRealClass();
	}

	/**
	 * Build whatever list wrapper {@code method} actually returns.
	 *
	 * Android 17 changed several IPackageManager list getters from ParceledListSlice to
	 * dedicated types such as android.content.pm.PackageInfoList. Handing back a slice
	 * regardless makes ApplicationPackageManager throw
	 * "Couldn't convert result of type android.content.pm.ParceledListSlice", which
	 * crash-loops the Play Store inside the container.
	 */
	public static Object create(Method method, List<?> list) {
		Class<?> returnType = method == null ? null : method.getReturnType();
		if (returnType == null || returnType == BRParceledListSlice.getRealClass()) {
			return create(list);
		}
		// The newer wrappers take the list straight off a constructor.
		try {
			return returnType.getConstructor(List.class).newInstance(list);
		} catch (Throwable ignored) {
		}
		// Some wrap a ParceledListSlice instead of the raw list.
		try {
			Object slice = create(list);
			return returnType.getConstructor(BRParceledListSlice.getRealClass())
					.newInstance(slice);
		} catch (Throwable ignored) {
		}
		return create(list);
	}

	public static Object create(List<?> list) {
		Object slice = BRParceledListSlice.get()._new(list);
		if (slice != null) {
			return slice;
		} else {
			slice = BRParceledListSlice.get()._new();
		}
		for (Object item : list) {
			BRParceledListSlice.get(slice).append(item);
		}
		BRParceledListSlice.get(slice).setLastSlice(true);
		return slice;
	}
}
