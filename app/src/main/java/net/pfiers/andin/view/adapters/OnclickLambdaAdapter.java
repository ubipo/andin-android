package net.pfiers.andin.view.adapters;

import android.view.View;

import androidx.databinding.BindingAdapter;

import kotlin.jvm.functions.Function1;

public class OnclickLambdaAdapter {
    @BindingAdapter("android:onClick")
    public static void setOnClick(View view, final Function1 callback) {
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                callback.invoke(v);
            }
        });
    }
}
