/**
* Android ownCloud News
*
* @author David Luhmer
* @copyright 2013 David Luhmer david-dev@live.de
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

package de.luhmer.owncloudnewsreader;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebBackForwardList;
import android.webkit.WebHistoryItem;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import de.luhmer.owncloudnewsreader.adapter.ProgressBarWebChromeClient;
import de.luhmer.owncloudnewsreader.async_tasks.RssItemToHtmlTask;
import de.luhmer.owncloudnewsreader.database.model.RssItem;
import de.luhmer.owncloudnewsreader.helper.AdBlocker;
import de.luhmer.owncloudnewsreader.helper.AsyncTaskHelper;
import de.luhmer.owncloudnewsreader.helper.ColorHelper;
import de.luhmer.owncloudnewsreader.services.DownloadWebPageService;

public class NewsDetailFragment extends Fragment implements RssItemToHtmlTask.Listener {

	public  static final String ARG_SECTION_NUMBER = "ARG_SECTION_NUMBER";
    private static final String RSS_ITEM_PAGE_URL = "about:blank";

	public final String TAG = getClass().getCanonicalName();

    protected @BindView(R.id.webview) WebView mWebView;
    protected @BindView(R.id.progressBarLoading) ProgressBar mProgressBarLoading;
    protected @BindView(R.id.progressbar_webview) ProgressBar mProgressbarWebView;
    protected @BindView(R.id.tv_offline_version) TextView mTvOfflineVersion;

    protected @Inject SharedPreferences mPrefs;

    private int section_number;
    protected String html;
    private GestureDetector mGestureDetector;


    public NewsDetailFragment() { }

    public int getSectionNumber() {
        return section_number;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((NewsReaderApplication) getActivity().getApplication()).getAppComponent().injectFragment(this);

        // Retain this fragment across configuration changes.
        setRetainInstance(true);

    }

    @Override
    public void onResume() {
        super.onResume();
        resumeCurrentPage();
    }

    @Override
	public void onPause() {
		super.onPause();
        pauseCurrentPage();
	}

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(mWebView != null) {
            mWebView.destroy();
        }
    }

    public void pauseCurrentPage() {
        if(mWebView != null) {
            mWebView.onPause();
            mWebView.pauseTimers();
        }
    }


    public void resumeCurrentPage() {
        if(mWebView != null) {
            mWebView.onResume();
            mWebView.resumeTimers();
        }
    }

    /**
     * @return true when calls to NewsDetailFragment#navigateBack()
     * can be processed right now
     * @see NewsDetailFragment#navigateBack()
     */
    public boolean canNavigateBack() {
        return !isCurrentPageRssItem();
    }

    /**
     * Navigates back to the last displayed page. Call NewsDetailFragment#canNavigateBack()
     * to check if back navigation is possible right now. Use e.g. for back button handling.
     * @see NewsDetailFragment#navigateBack()
     */
    public void navigateBack() {
        if (isLastPageRssItem()) {
            mWebView.clearHistory();
            startLoadRssItemToWebViewTask();
        } else if (!isCurrentPageRssItem()){
            mWebView.goBack();
        }
    }

    @Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_news_detail, container, false);

		section_number = (Integer) getArguments().get(ARG_SECTION_NUMBER);

        ButterKnife.bind(this, rootView);

        // Do not reload webview if retained
        if(savedInstanceState == null) {
            startLoadRssItemToWebViewTask();
        } else {
            mWebView.restoreState(savedInstanceState);
            mProgressBarLoading.setVisibility(View.GONE);
            // Make sure to sync the incognitio on retained views
            syncIncognitoState();
            this.addBottomPaddingForFastActions(mWebView);
        }

        setUpGestureDetector();

		return rootView;
	}

	protected void syncIncognitoState() {
        NewsDetailActivity ndActivity = ((NewsDetailActivity)getActivity());
        boolean isIncognito = ndActivity.isIncognitoEnabled();
        mWebView.getSettings().setBlockNetworkLoads(isIncognito);
        mWebView.getSettings().setBlockNetworkImage(isIncognito);
    }

	@Override
    public void onSaveInstanceState(Bundle outState) {
        mWebView.saveState(outState);
    }

	private void setUpGestureDetector() {
        mGestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener());

        mGestureDetector.setOnDoubleTapListener(new GestureDetector.OnDoubleTapListener()
        {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                return false;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                Log.v(TAG, "onDoubleTap() called with: e = [" + e + "]");
                NewsDetailActivity ndActivity = ((NewsDetailActivity)getActivity());
                if(ndActivity != null) {
                    ((NewsDetailActivity) getActivity()).toggleRssItemStarredState();

                    // Star has 5 corners. So we can rotate it by 2/5
                    View view = getActivity().findViewById(R.id.action_starred);
                    ObjectAnimator animator = ObjectAnimator.ofFloat(view, "rotation", view.getRotation() + (2*(360f/5f)));
                    animator.start();
                }
                return false;
            }

            @Override
            public boolean onDoubleTapEvent(MotionEvent e) {
                return false;
            }
        });
    }

    protected void startLoadRssItemToWebViewTask() {
        Log.d(TAG, "startLoadRssItemToWebViewTask() called");
        mWebView.setVisibility(View.GONE);
        mProgressBarLoading.setVisibility(View.VISIBLE);

        NewsDetailActivity ndActivity = ((NewsDetailActivity)getActivity());
        assert ndActivity != null;

        int backgroundColor = ContextCompat.getColor(ndActivity, R.color.news_detail_background_color);
        mWebView.setBackgroundColor(backgroundColor);
        ndActivity.setBackgroundColorOfViewPager(backgroundColor);

        init_webView();
        RssItem rssItem = ndActivity.rssItems.get(section_number);
        RssItemToHtmlTask task = new RssItemToHtmlTask(ndActivity, rssItem, this, mPrefs);
        AsyncTaskHelper.StartAsyncTask(task);
    }

    @Override
    public void onRssItemParsed(String htmlPage) {
        mWebView.setVisibility(View.VISIBLE);
        mProgressBarLoading.setVisibility(View.GONE);

        setSoftwareRenderModeForWebView(htmlPage, mWebView);

        html = htmlPage;
        mWebView.loadDataWithBaseURL("file:///android_asset/", htmlPage, "text/html", "UTF-8", RSS_ITEM_PAGE_URL);
    }

    /**
     * This function has no effect on devices with api level < HONEYCOMB
     * @param htmlPage
     * @param webView
     */
    private void setSoftwareRenderModeForWebView(String htmlPage, WebView webView) {
        if (htmlPage.contains(".gif")) {
            if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                // Workaround some playback issues with gifs on devices below android oreo
                webView.setLayerType(WebView.LAYER_TYPE_SOFTWARE, null);
            }

            Log.v("NewsDetailFragment", "Using LAYER_TYPE_SOFTWARE");
        } else {
            if (webView.getLayerType() == WebView.LAYER_TYPE_HARDWARE) {
                Log.v("NewsDetailFragment", "Using LAYER_TYPE_HARDWARE");
            } else if (webView.getLayerType() == WebView.LAYER_TYPE_SOFTWARE) {
                Log.v("NewsDetailFragment", "Using LAYER_TYPE_SOFTWARE");
            } else {
                Log.v("NewsDetailFragment", "Using LAYER_TYPE_DEFAULT");
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
	private void init_webView() {
        int backgroundColor = ColorHelper.getColorFromAttribute(getContext(),
                R.attr.news_detail_background_color);
        mWebView.setBackgroundColor(backgroundColor);

        WebSettings webSettings = mWebView.getSettings();
        //webSettings.setPluginState(WebSettings.PluginState.ON);
        webSettings.setJavaScriptEnabled(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(false);
        webSettings.setSupportMultipleWindows(false);
        webSettings.setSupportZoom(false);
        webSettings.setAppCacheEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(true);

        syncIncognitoState();

        registerForContextMenu(mWebView);

        mWebView.setWebChromeClient(new ProgressBarWebChromeClient(mProgressbarWebView));

        mWebView.setWebViewClient(new WebViewClient() {

            private Map<String, Boolean> loadedUrls = new HashMap<>();

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                //Log.d(TAG, "shouldInterceptRequest: " + url);

                boolean isAd;
                if (!loadedUrls.containsKey(url)) {
                    isAd = AdBlocker.isAd(url);
                    loadedUrls.put(url, isAd);
                } else {
                    isAd = loadedUrls.get(url);
                }
                return isAd ? AdBlocker.createEmptyResource() : super.shouldInterceptRequest(view, url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                NewsDetailFragment.this.loadURL(url);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                addBottomPaddingForFastActions(view);
            }
        });

        mWebView.setOnTouchListener((v, event) -> {
            mGestureDetector.onTouchEvent(event);

            /*
            if (v.getId() == R.id.webview && event.getAction() == MotionEvent.ACTION_DOWN) {
                changedUrl = true;
            }
            */

            return false;
        });
	}

    /**
     * Add free space to bottom of web-site if Fast-Actions are switched on.
     * Otherwise the fast action bar might hide the article content.
     * Method to modify the body margins with JavaScript seems to be dirty, but no other
     * solution seems to be available.
     *
     * This method does (for unknown reasons) not work if WebView gets restored. The Javascript is
     * called but not executed.
     *
     * This is (only) a problem, if user swipes back in viewpager to already loaded articles.
     * Solution might be to switch to a different design.
     *  - Bottom App Bar -- overall cleanest solution but interferes with current implementation
     *    of Podcast Player
     *  - Auto-hiding ActionBar. Hard to implement as scroll behaviour of WebView has to be used
     *    for hiding/showing ActionBar.
     *
     * @param view WebView with article
     */
	private void addBottomPaddingForFastActions(WebView view) {
        if (mPrefs.getBoolean(SettingsActivity.CB_SHOW_FAST_ACTIONS,true)) {
            view.loadUrl("javascript:document.body.style.marginBottom=\"100px\"; void 0");
        }
    }

    /**
     * Loads the given url in the selected view based on user settings (Custom Chrome Tabs, webview or external)
     *
     * @param url address to load
     */

	public void loadURL(String url) {
        int selectedBrowser = Integer.parseInt(mPrefs.getString(SettingsActivity.SP_DISPLAY_BROWSER, "0"));

        File webArchiveFile = DownloadWebPageService.getWebPageArchiveFileForUrl(getActivity(), url);
        if(webArchiveFile.exists()) { // Test if WebArchive exists for url
            mTvOfflineVersion.setVisibility(View.VISIBLE);
            mWebView.loadUrl("file://" + webArchiveFile.getAbsolutePath());
        } else {
            mTvOfflineVersion.setVisibility(View.GONE);
            switch (selectedBrowser) {
                case 0: // Custom Tabs
                    CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder()
                            .setToolbarColor(ContextCompat.getColor(getActivity(), R.color.colorPrimary))
                            .setShowTitle(true)
                            .setStartAnimations(getActivity(), R.anim.slide_in_right, R.anim.slide_out_left)
                            .setExitAnimations(getActivity(), R.anim.slide_in_left, R.anim.slide_out_right)
                            .addDefaultShareMenuItem();
                    builder.build().launchUrl(getActivity(), Uri.parse(url));
                    break;
                case 1: // External Browser
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(browserIntent);
                    break;
                case 2: // Built in
                    mWebView.loadUrl(url);
                    break;
                default:
                    throw new IllegalStateException("Unknown selection!");
            }
        }
    }


    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo) {
        if (!(view instanceof WebView))
            return;

        WebView.HitTestResult result = ((WebView) view).getHitTestResult();
        if (result == null)
            return;

        int type = result.getType();
        Document htmlDoc = Jsoup.parse(html);
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        String text;
        DialogFragment newFragment;


        switch (type) {
            case WebView.HitTestResult.IMAGE_TYPE:
            case WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE:
                String imageUrl = result.getExtra();

                if (imageUrl.startsWith("http") || imageUrl.startsWith("file")) {
                    URL mImageUrl;
                    String imgtitle;
                    String imgaltval;
                    String imgsrcval;

                    imgsrcval = imageUrl.substring(imageUrl.lastIndexOf('/') + 1);
                    Elements imgtag = htmlDoc.getElementsByAttributeValueContaining("src", imageUrl);

                    try {
                        imgtitle = imgtag.first().attr("title");
                    } catch (NullPointerException e) {
                        imgtitle = "";
                    }
                    try {
                        imgaltval = imgtag.first().attr("alt");
                    } catch (NullPointerException e) {
                        imgaltval = "";
                    }
                    try {
                        mImageUrl = new URL(imageUrl);
                    } catch (MalformedURLException e) {
                        return;
                    }

                    String title = imgsrcval;
                    int titleIcon = android.R.drawable.ic_menu_gallery;
                    text = (imgtitle.isEmpty()) ? imgaltval : imgtitle;

                    // Create and show the dialog.
                    newFragment = NewsDetailImageDialogFragment.newInstanceImage(title, titleIcon, text, mImageUrl);
                    newFragment.show(ft, "menu_fragment_dialog");
                }
                break;

            case WebView.HitTestResult.SRC_ANCHOR_TYPE:
                String url = result.getExtra();
                URL mUrl;
                try {
                    Elements urltag = htmlDoc.getElementsByAttributeValueContaining("href", url);
                    text = urltag.text();
                    mUrl = new URL(url);
                } catch (MalformedURLException e) {
                    return;
                }

                // Create and show the dialog.
                newFragment = NewsDetailImageDialogFragment.newInstanceUrl(text, mUrl.toString());
                newFragment.show(ft, "menu_fragment_dialog");
                break;
            case WebView.HitTestResult.EMAIL_TYPE:
            case WebView.HitTestResult.GEO_TYPE:
            case WebView.HitTestResult.PHONE_TYPE:
            case WebView.HitTestResult.EDIT_TEXT_TYPE:
                break;
            default:
                Log.v(TAG, "Unknown type: " + type + ". Skipping..");
        }
    }

    /**
     * @return true when the last page on the webview's history stack is
     * the original rss item page
     */
    private boolean isLastPageRssItem() {
        WebBackForwardList list = mWebView.copyBackForwardList();
        WebHistoryItem lastItem = list.getItemAtIndex(list.getCurrentIndex() - 1);
        return lastItem != null && lastItem.getUrl().equals(RSS_ITEM_PAGE_URL);
    }

    /**
     * @return true when the current page on the webview's history stack is
     * the original rss item page
     */
    private boolean isCurrentPageRssItem() {
        if(mWebView.copyBackForwardList().getCurrentItem() != null) {
            String currentPageUrl = mWebView.copyBackForwardList().getCurrentItem().getOriginalUrl();
            return currentPageUrl.equals("data:text/html;charset=utf-8;base64,");
        }
        return true;
    }
}
