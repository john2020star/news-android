package de.luhmer.owncloudnewsreader.adapter;

import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.format.DateUtils;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.View;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;

import org.greenrobot.eventbus.Subscribe;

import java.util.List;
import java.util.regex.Pattern;

import butterknife.BindView;
import butterknife.ButterKnife;
import de.luhmer.owncloudnewsreader.R;
import de.luhmer.owncloudnewsreader.SettingsActivity;
import de.luhmer.owncloudnewsreader.async_tasks.RssItemToHtmlTask;
import de.luhmer.owncloudnewsreader.database.DatabaseConnectionOrm;
import de.luhmer.owncloudnewsreader.database.model.RssItem;
import de.luhmer.owncloudnewsreader.helper.ColorHelper;
import de.luhmer.owncloudnewsreader.helper.FavIconHandler;
import de.luhmer.owncloudnewsreader.helper.ImageHandler;
import de.luhmer.owncloudnewsreader.helper.SquareRoundedBitmapDisplayer;
import de.luhmer.owncloudnewsreader.services.PodcastDownloadService;

import static android.view.View.GONE;

public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
    private final static String TAG = "RecyclerView.ViewHolder";

    private static SparseArray<Integer> downloadProgressList = new SparseArray<>();

    @Nullable
    @BindView(R.id.star_imageview)
    protected ImageView starImageView;

    @BindView(R.id.summary)
    protected TextView textViewSummary;

    @BindView(R.id.tv_item_date)
    protected TextView textViewItemDate;

    @BindView(R.id.tv_subscription)
    protected TextView textViewTitle;

    @Nullable
    @BindView(R.id.imgViewFavIcon)
    protected ImageView imgViewFavIcon;

    @Nullable
    @BindView(R.id.imgViewThumbnail)
    protected ImageView imgViewThumbnail;


    @Nullable
    @BindView(R.id.color_line_feed)
    protected View colorLineFeed;

    @BindView(R.id.btn_playPausePodcast)
    protected ImageView btnPlayPausePodcast;

    @BindView(R.id.podcastDownloadProgress)
    //protected HoloCircularProgressBar pbPodcastDownloadProgress; // TODO reminder: remove this dependency (and fix podcast progressbar issues)
    protected ProgressBar pbPodcastDownloadProgress;

    @BindView(R.id.podcast_wrapper)
    View flPlayPausePodcastWrapper;

    // only in extended layout
    @Nullable @BindView(R.id.body)
    protected TextView textViewBody;

    // Only in extended with webview layout
    @Nullable @BindView(R.id.webView_body)
    protected WebView webView_body;

    private RecyclerItemClickListener clickListener;
    private RssItem rssItem;
    private boolean stayUnread = false;
    private static FavIconHandler favIconHandler = null;
    private final int LengthBody = 400;
    private ForegroundColorSpan bodyForegroundColor;
    private boolean playing;
    private int selectedListLayout;
    private int starColor;
    private int inactiveStarColor;
    private DisplayImageOptions displayImageOptionsThumbnail;

    private int textSizeSummary;
    private int textSizeTitle;
    private int textSizeItemDate;
    private int textSizeBody = -1;

    private SharedPreferences mPrefs;

    public ViewHolder(View itemView, SharedPreferences prefs) {
        super(itemView);

        this.mPrefs = prefs;

        selectedListLayout = Integer.parseInt(mPrefs.getString(SettingsActivity.SP_FEED_LIST_LAYOUT, "0"));

        bodyForegroundColor = new ForegroundColorSpan(ContextCompat.getColor(itemView.getContext(), android.R.color.secondary_text_dark));

        if(favIconHandler == null) {
            favIconHandler = new FavIconHandler(itemView.getContext());
        }

        ButterKnife.bind(this, itemView);

        int[] attribute = new int[]{ R.attr.starredColor, R.attr.unstarredColor };
        TypedArray array = starImageView.getContext().getTheme().obtainStyledAttributes(attribute);
        starColor = array.getColor(0, Color.TRANSPARENT);
        inactiveStarColor = array.getColor(1, Color.LTGRAY);
        array.recycle();

        // get and store initial item text sizes (can't figure out how to directly get this info from layout definition)
        textSizeSummary = Math.round(textViewSummary.getTextSize());
        textSizeTitle = Math.round(textViewTitle.getTextSize());
        if(textViewBody != null) {
            textSizeBody = Math.round(textViewBody.getTextSize());
        }
        textSizeItemDate = Math.round(textViewItemDate.getTextSize());

        itemView.setOnClickListener(this);
        itemView.setOnLongClickListener(this);

        Drawable feedIcon = VectorDrawableCompat.create(itemView.getResources(), R.drawable.feed_icon, null) ;
        displayImageOptionsThumbnail = new DisplayImageOptions.Builder()
                .displayer(new SquareRoundedBitmapDisplayer(30))
                .showImageOnLoading(feedIcon)
                .showImageForEmptyUri(feedIcon)
                .showImageOnFail(feedIcon)
                .cacheOnDisk(true)
                .cacheInMemory(true)
                .build();
    }

    @Subscribe
    public void onEvent(PodcastDownloadService.DownloadProgressUpdate downloadProgress) {
        downloadProgressList.put((int) downloadProgress.podcast.itemId, downloadProgress.podcast.downloadProgress);
        if (rssItem.getId().equals(downloadProgress.podcast.itemId)) {
            pbPodcastDownloadProgress.setProgress(downloadProgress.podcast.downloadProgress);

            Log.v(TAG, "Progress of download1: " + downloadProgress.podcast.downloadProgress);
        }
    }

    public void setDownloadPodcastProgressbar() {
        float progress;
        if(PodcastDownloadService.PodcastAlreadyCached(itemView.getContext(), rssItem.getEnclosureLink())) {
            progress = 100;
        } else {
            if(downloadProgressList.get(rssItem.getId().intValue()) != null) {
                progress = downloadProgressList.get(rssItem.getId().intValue());
            } else {
                progress = 0;
            }
        }
        pbPodcastDownloadProgress.setProgress((int) progress);
        Log.v(TAG, "Progress of download2: " + progress);
    }

    @Override
    public void onClick(View v) {
        clickListener.onClick(this, getLayoutPosition());
    }

    public void setClickListener(RecyclerItemClickListener clickListener) {
        this.clickListener = clickListener;
    }

    @Override
    public boolean onLongClick(View v) {
        return clickListener.onLongClick(this, getLayoutPosition());
    }

    private void setFeedColor(int color) {
        if(colorLineFeed != null) {
            colorLineFeed.setBackgroundColor(color);
        }
    }

    public void setReadState(boolean isRead) {
        if(textViewSummary != null) {
            float alpha = 1f;
            if (isRead) {
                textViewSummary.setTypeface(Typeface.DEFAULT);
                alpha = 0.7f;
            } else {
                textViewSummary.setTypeface(Typeface.DEFAULT_BOLD);
            }

            ((View) textViewSummary.getParent()).setAlpha(alpha);
        }
    }

    public void setStarred(boolean isStarred) {
        int color = isStarred ? starColor : inactiveStarColor;
        int contentDescriptionId = isStarred ?
                R.string.content_desc_remove_from_favorites :
                R.string.content_desc_add_to_favorites;
        starImageView.setColorFilter(color);
        starImageView.setContentDescription(starImageView.getContext().getString(contentDescriptionId));
    }

    public void setPlaying(boolean playing) {
        this.playing = playing;
        
        int imageId = playing ? R.drawable.ic_action_pause : R.drawable.ic_action_play;
        int contentDescriptionId = playing ? R.string.content_desc_pause : R.string.content_desc_play;

        String contentDescription = btnPlayPausePodcast.getContext().getString(contentDescriptionId);
        btnPlayPausePodcast.setContentDescription(contentDescription);
        btnPlayPausePodcast.setImageResource(imageId);
    }

    public boolean isPlaying() {
        return playing;
    }

    public RssItem getRssItem() {
        return rssItem;
    }

    public void setRssItem(RssItem rssItem) {
        this.rssItem = rssItem;
        String title = null;
        String favIconUrl = null;
        if(rssItem.getFeed() != null) {
            title = rssItem.getFeed().getFeedTitle();
            favIconUrl = rssItem.getFeed().getFaviconUrl();
        } else {
            Log.v(TAG, "Feed not found!!!");
        }

        setReadState(rssItem.getRead_temp());
        setStarred(rssItem.getStarred_temp());

        setFeedColor(ColorHelper.getFeedColor(itemView.getContext(), rssItem.getFeed()));

        if(textViewSummary != null) {
            try {
                //byte[] arrByteForSpanish = rssItem.getTitle().getBytes("ISO-8859-1");
                //String spanish = new String(arrByteForSpanish);//.getBytes("UTF-8");
                //textViewSummary.setText(Html.fromHtml(spanish));

                textViewSummary.setText(Html.fromHtml(rssItem.getTitle()));
                scaleTextSize(textViewSummary, textSizeSummary, false, mPrefs);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if(textViewTitle != null && title != null) {
            textViewTitle.setText(Html.fromHtml(title));
            scaleTextSize(textViewTitle, textSizeTitle, true, mPrefs);
        }

        if(textViewBody != null) {
            String body = rssItem.getBody();
            // Strip html from String
            if(selectedListLayout == 0) {
                textViewBody.setMaxLines(scaleTextLines(mPrefs));
                body = getBodyText(body, false);

            } else if(selectedListLayout == 3) {
                textViewBody.setMaxLines(200);
                body = getBodyText(body, false);

            } else {
                body = getBodyText(body, true);
            }
            textViewBody.setText(Html.fromHtml(body));
            scaleTextSize(textViewBody, textSizeBody, false, mPrefs);
        }

        int height = 0; // used for feed icon vertical offset calculation
        if(textViewItemDate != null) {
            textViewItemDate.setText(DateUtils.getRelativeTimeSpanString(rssItem.getPubDate().getTime()));
            scaleTextSize(textViewItemDate, textSizeItemDate, true, mPrefs);
            height = Math.round(textViewItemDate.getTextSize());
        }

        if (imgViewFavIcon != null) {
            favIconHandler.loadFavIconForFeed(favIconUrl, imgViewFavIcon, Math.round((height-textSizeItemDate)/2));
        }

        if(imgViewThumbnail != null) {
            imgViewThumbnail.setColorFilter(null);
            String body = rssItem.getBody();
            List<String> images = ImageHandler.getImageLinksFromText(body);

            if(images.size() > 0) {
                imgViewThumbnail.setVisibility(View.VISIBLE);
                ImageLoader.getInstance().displayImage(images.get(0), imgViewThumbnail, displayImageOptionsThumbnail);
            } else {
                // Show Podcast Icon if no thumbnail is available but it is a podcast (otherwise the podcast button will go missing)
                if (DatabaseConnectionOrm.ALLOWED_PODCASTS_TYPES.contains(rssItem.getEnclosureMime())) {
                    imgViewThumbnail.setVisibility(View.VISIBLE);
                    //imgViewThumbnail.setColorFilter(Color.parseColor("#d8d8d8"));
                    Drawable feedIcon = VectorDrawableCompat.create(itemView.getResources(), R.drawable.feed_icon, null) ;
                    imgViewThumbnail.setImageDrawable(feedIcon);
                } else {
                    imgViewThumbnail.setVisibility(GONE);
                }
            }
        }

        if(webView_body != null) {
            String htmlPage = RssItemToHtmlTask.getHtmlPage(rssItem, false, mPrefs, itemView.getContext());
            webView_body.loadDataWithBaseURL("file:///android_asset/", htmlPage, "text/html", "UTF-8", "");
        }
    }

    /**
     * Apply scaling factor to TextView font size, based on app font-size preference.
     *
     * @param tv            TextView object to be scaled
     * @param initialTvSize   app layout definition default size of TextView element
     * @param halfScale     if set to true, will only apply half of the scaling factor
     */
    private static void scaleTextSize(TextView tv, int initialTvSize, boolean halfScale, SharedPreferences mPrefs) {
        float scalingFactor = Float.parseFloat(mPrefs.getString(SettingsActivity.SP_FONT_SIZE, "1.0"));
        if(halfScale) {
            scalingFactor = scalingFactor + (1-scalingFactor)/2;
        }

        int initialSize = initialTvSize;
        if(initialSize < 0) {
            initialSize = Math.round(tv.getTextSize());
        }
        // float sp = initialSize / tv.getContext().getResources().getDisplayMetrics().scaledDensity;  // transform scaled pixels, device pixels
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.round(initialSize*scalingFactor));
    }

    /**
     * Return the number of rss item body text lines, depending on the currently selected font size/scale;
     * only meant to be used with thumbnail feed view.
     *
     * @return      number of lines of rss item body text lines to be used in thumbnail feed view
     */
    private static int scaleTextLines(SharedPreferences prefs) {
        float scalingFactor = Float.parseFloat(prefs.getString(SettingsActivity.SP_FONT_SIZE, "1.0"));
        /* The following formula computes the number of text lines for Simple item view; it simply boils
         * down to a linear conversion from the font scaling factor from 0.8 -> 6 lines to 1.6 -> 3 lines
         */
        return Math.round((scalingFactor*-5)+10);
    }


    public boolean shouldStayUnread() {
        return stayUnread;
    }

    public void setStayUnread(boolean shouldStayUnread) {
        this.stayUnread = shouldStayUnread;
    }

    private String getBodyText(String body, boolean limitLength)
    {
        if (body.startsWith("<![CDATA[")) {
            body = body.replaceFirst( Pattern.quote("<![CDATA["), "");
            body = body.replaceFirst("]]>", "");
        }

        body = body.replaceAll("<img[^>]*>", "");
        body = body.replaceAll("<video[^>]*>", "");

        SpannableString bodyStringSpannable = new SpannableString(Html.fromHtml(body));
        bodyStringSpannable.setSpan(bodyForegroundColor, 0, bodyStringSpannable.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);

        String bodyString = bodyStringSpannable.toString().trim();


        if(limitLength && bodyString.length() > LengthBody) {
            bodyString = bodyString.substring(0, LengthBody) + "...";
        }

        return bodyString;
    }
}
