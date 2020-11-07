package net.pfiers.andin.view.adapters;

import android.graphics.Typeface;
import android.view.View;
import android.widget.TextView;

import androidx.databinding.BindingAdapter;

public class VisibilityAdapter {
    @BindingAdapter("android:visibility")
    public static void setStyle(View view, Boolean show) {
        view.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}
