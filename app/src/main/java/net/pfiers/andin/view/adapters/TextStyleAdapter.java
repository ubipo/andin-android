package net.pfiers.andin.view.adapters;

import android.graphics.Typeface;
import android.util.Log;
import android.widget.TextView;

import androidx.databinding.BindingAdapter;
import androidx.databinding.BindingMethod;
import androidx.databinding.BindingMethods;

public class TextStyleAdapter {
    @BindingAdapter("android:textStyle")
    public static void setStyle(TextView textView, String style) {
        Integer face = null;
        switch (style) {
            case "normal":
                face = Typeface.NORMAL; break;
            case "bold":
                face = Typeface.BOLD; break;
            case "cursive":
                face = Typeface.ITALIC; break;
            case "bold_cursive":
                face = Typeface.BOLD_ITALIC; break;
        }
        if (face != null)
            textView.setTypeface(textView.getTypeface(), face);
    }
}
