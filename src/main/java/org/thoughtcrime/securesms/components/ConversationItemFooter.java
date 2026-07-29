package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import chat.delta.rpc.Rpc;
import chat.delta.rpc.RpcException;
import com.b44t.messenger.DcChat;
import com.b44t.messenger.DcContact;
import com.b44t.messenger.DcContext;
import com.b44t.messenger.DcMsg;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.connect.DcHelper;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.Prefs;

public class ConversationItemFooter extends LinearLayout {

  private TextView dateView;
  private TextView editedView;
  private TextView viewsLabel;
  private ImageView viewsIcon;
  private ImageView bookmarkIndicatorView;
  private ImageView emailIndicatorView;
  private ImageView locationIndicatorView;
  private DeliveryStatusView deliveryStatusView;
  private Integer textColor = null;
  private Context context;
  private Rpc rpc;

  public ConversationItemFooter(Context context) {
    super(context);
    init(context, null);
  }

  public ConversationItemFooter(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init(context, attrs);
  }

  public ConversationItemFooter(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init(context, attrs);
  }

  private void init(Context context, @Nullable AttributeSet attrs) {
    this.context = context;
    this.rpc = DcHelper.getRpc(context);
    inflate(getContext(), R.layout.conversation_item_footer, this);

    dateView = findViewById(R.id.footer_date);
    editedView = findViewById(R.id.footer_edited);
    viewsLabel = findViewById(R.id.footer_views);
    viewsIcon = findViewById(R.id.footer_views_icon);
    bookmarkIndicatorView = findViewById(R.id.footer_bookmark_indicator);
    emailIndicatorView = findViewById(R.id.footer_email_indicator);
    locationIndicatorView = findViewById(R.id.footer_location_indicator);
    deliveryStatusView = new DeliveryStatusView(findViewById(R.id.delivery_indicator));

    if (attrs != null) {
      try (TypedArray typedArray =
          getContext()
              .getTheme()
              .obtainStyledAttributes(attrs, R.styleable.ConversationItemFooter, 0, 0)) {
        setTextColor(
            typedArray.getInt(
                R.styleable.ConversationItemFooter_footer_text_color,
                getResources().getColor(R.color.core_white)));
      }
    }
  }

  public void setMessageRecord(@NonNull DcMsg messageRecord) {
    presentDate(messageRecord);
    boolean bookmark = messageRecord.getOriginalMsgId() != 0 || messageRecord.getSavedMsgId() != 0;
    bookmarkIndicatorView.setVisibility(bookmark ? View.VISIBLE : View.GONE);
    editedView.setVisibility(messageRecord.isEdited() ? View.VISIBLE : View.GONE);

    int downloadState = messageRecord.getDownloadState();
    if (messageRecord.isSecure()
        || downloadState == DcMsg.DC_DOWNLOAD_AVAILABLE
        || downloadState == DcMsg.DC_DOWNLOAD_FAILURE
        || downloadState == DcMsg.DC_DOWNLOAD_IN_PROGRESS) {
      emailIndicatorView.setVisibility(View.GONE);
    } else {
      emailIndicatorView.setVisibility(View.VISIBLE);
    }

    locationIndicatorView.setVisibility(messageRecord.hasLocation() ? View.VISIBLE : View.GONE);

    boolean isOutChannel =
        DcHelper.getContext(context).getChat(messageRecord.getChatId()).isOutBroadcast();

    if (isOutChannel && messageRecord.isOutgoing()) {
      try {
        int accId = rpc.getSelectedAccountId();
        int count = rpc.getMessageReadReceiptCount(accId, messageRecord.getId());
        viewsLabel.setText(String.format("%d", count));
        viewsLabel.setVisibility(View.VISIBLE);
        viewsIcon.setVisibility(View.VISIBLE);
      } catch (RpcException e) {
        e.printStackTrace();
      }
    } else {
      viewsLabel.setVisibility(View.GONE);
      viewsIcon.setVisibility(View.GONE);
    }

    presentDeliveryStatus(messageRecord, isOutChannel);
  }

  private void setTextColor(int color) {
    textColor = color;
    dateView.setTextColor(color);
    editedView.setTextColor(color);
    viewsLabel.setTextColor(color);
    viewsIcon.setColorFilter(color);
    bookmarkIndicatorView.setColorFilter(color);
    emailIndicatorView.setColorFilter(color);
    locationIndicatorView.setColorFilter(color);
    deliveryStatusView.setTint(color);
  }

  private void presentDate(@NonNull DcMsg dcMsg) {
    dateView.forceLayout();
    dateView.setText(
        DateUtils.getExtendedRelativeTimeSpanString(getContext(), dcMsg.getTimestamp()));
  }

  private void presentDeliveryStatus(@NonNull DcMsg messageRecord, boolean isOutChannel) {
    // isDownloading is temporary and should be checked first.
    boolean isDownloading = messageRecord.getDownloadState() == DcMsg.DC_DOWNLOAD_IN_PROGRESS;

    if (isDownloading) deliveryStatusView.setDownloading();
    else if (messageRecord.isPending()) deliveryStatusView.setPending();
    else if (messageRecord.isFailed()) deliveryStatusView.setFailed();
    else if (!messageRecord.isOutgoing() || isOutChannel) deliveryStatusView.setNone();
    else if (messageRecord.isRemoteRead()) {
      // shiroikuma fork (Step 11): opt-in extra rung - in a group, tell "read by some" apart from
      // "read by every other member" via the per-message read-receipt count. Off by default: it
      // costs one JSON-RPC call per outgoing group row on the bind path.
      if (Prefs.isTickGroupAll(context) && isReadByAllMembers(messageRecord)) {
        deliveryStatusView.setReadByAll();
      } else {
        deliveryStatusView.setRead();
      }
    } else if (messageRecord.isDelivered()) deliveryStatusView.setSent();
    else if (messageRecord.isPreparing()) deliveryStatusView.setPreparing();
    else deliveryStatusView.setPending();
  }

  /**
   * shiroikuma fork (Step 11): true when every other member of a group chat has returned a read
   * receipt for this message. Best-effort - any failure falls back to the plain "received" tick.
   */
  private boolean isReadByAllMembers(@NonNull DcMsg messageRecord) {
    try {
      DcContext dcContext = DcHelper.getContext(context);
      DcChat chat = dcContext.getChat(messageRecord.getChatId());
      if (!chat.isMultiUser()) return false;
      int others = 0;
      for (int contactId : dcContext.getChatContacts(chat.getId())) {
        if (contactId != DcContact.DC_CONTACT_ID_SELF) others++;
      }
      if (others <= 0) return false;
      int read = rpc.getMessageReadReceiptCount(rpc.getSelectedAccountId(), messageRecord.getId());
      return read >= others;
    } catch (Exception e) {
      return false;
    }
  }

  public String getDescription() {
    String desc = dateView.getText().toString();
    String deliveryDesc = deliveryStatusView.getDescription();
    if (!"".equals(deliveryDesc)) {
      desc += "\n" + deliveryDesc;
    }
    return desc;
  }
}
