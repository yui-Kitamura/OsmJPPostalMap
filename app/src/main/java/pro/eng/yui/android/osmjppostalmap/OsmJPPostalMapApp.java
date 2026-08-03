package pro.eng.yui.android.osmjppostalmap;

import android.app.Application;
import pro.eng.yui.android.osmjppostalmap.data.repository.SettingsRepository;

public class OsmJPPostalMapApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        new SettingsRepository(this).applyCurrentTheme();
    }
}
