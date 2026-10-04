package com.android.killapps.fixture;
import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
public class FixtureActivity extends Activity {
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); TextView text = new TextView(this);
        text.setText("Disposable target for KillApps device verification"); setContentView(text);
    }
}
