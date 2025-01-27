/**
 * Android ownCloud News
 *
 * @author David Luhmer
 * @copyright 2019 David Luhmer david-dev@live.de
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU AFFERO GENERAL PUBLIC LICENSE
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU AFFERO GENERAL PUBLIC LICENSE for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with this library.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package de.luhmer.owncloudnewsreader.helper;

import android.app.Activity;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.SearchView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.appcompat.widget.ActionMenuView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import java.lang.reflect.Field;

import de.luhmer.owncloudnewsreader.R;

public class ThemeUtils {

    private static final String TAG = ThemeUtils.class.getCanonicalName();

    private ThemeUtils() {}

    /**
     * Sets the color of the SearchView to {@code color} (cursor.
     * @param searchView
     */
    public static void colorSearchViewCursorColor(SearchView searchView, @ColorInt int color) {
        try {
            Field searchTextViewRef = SearchView.class.getDeclaredField("mSearchSrcTextView");
            searchTextViewRef.setAccessible(true);
            Object searchAutoComplete = searchTextViewRef.get(searchView);

            Field mCursorDrawableRes = TextView.class.getDeclaredField("mCursorDrawableRes");
            mCursorDrawableRes.setAccessible(true);
            mCursorDrawableRes.set(searchAutoComplete, R.drawable.cursor);


            // Set color of handle
            // https://stackoverflow.com/a/49555923

            //get the pointer resource id
            Field textSelectHandleRef = TextView.class.getDeclaredField("mTextSelectHandleRes");
            textSelectHandleRef.setAccessible(true);
            int drawableResId = textSelectHandleRef.getInt(searchAutoComplete);

            //get the editor
            Field editorRef = TextView.class.getDeclaredField("mEditor");
            editorRef.setAccessible(true);
            Object editor = editorRef.get(searchAutoComplete);

            //tint drawable
            Drawable drawable = ContextCompat.getDrawable(searchView.getContext(), drawableResId);
            drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN);

            //set the drawable
            Field mSelectHandleCenter = editor.getClass().getDeclaredField("mSelectHandleCenter");
            mSelectHandleCenter.setAccessible(true);
            mSelectHandleCenter.set(editor, drawable);

            Field mSelectHandleLeft = editor.getClass().getDeclaredField("mSelectHandleLeft");
            mSelectHandleLeft.setAccessible(true);
            mSelectHandleLeft.set(editor, drawable);

            Field mSelectHandleRight = editor.getClass().getDeclaredField("mSelectHandleRight");
            mSelectHandleRight.setAccessible(true);
            mSelectHandleRight.set(editor, drawable);
        } catch (Exception e) {
            Log.e(TAG, "Couldn't apply color to search view cursor", e);
        }
    }


    /**
     * Use this method to colorize the toolbar to the desired target color
     * @param toolbarView toolbar view being colored
     * @param toolbarBackgroundColor the target background color
     */
    public static void colorizeToolbar(Toolbar toolbarView, @ColorInt int toolbarBackgroundColor) {

        toolbarView.setBackgroundColor(toolbarBackgroundColor);

        for(int i = 0; i < toolbarView.getChildCount(); i++) {
            final View v = toolbarView.getChildAt(i);

            v.setBackgroundColor(toolbarBackgroundColor);

            if(v instanceof ActionMenuView) {
                for(int j = 0; j < ((ActionMenuView)v).getChildCount(); j++) {
                    v.setBackgroundColor(toolbarBackgroundColor);
                }
            }
        }
    }

    /**
     * Use this method to colorize the status bar to the desired target color
     * @param activity
     * @param statusBarColor
     */
    public static void changeStatusBarColor(Activity activity, @ColorInt int statusBarColor) {
        Window window = activity.getWindow();
        // clear FLAG_TRANSLUCENT_STATUS flag:
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        // add FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS flag to the window
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(statusBarColor);
        }
    }
}
