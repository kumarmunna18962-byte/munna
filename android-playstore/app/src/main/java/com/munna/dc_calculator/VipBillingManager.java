package com.munna.dc_calculator;

import android.app.Activity;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;
import com.android.billingclient.api.*;
import java.util.*;

/** Play Store subscription bridge. Plans must exist in Play Console before checkout can work. */
public final class VipBillingManager {
    public static final String MONTHLY = "dc_vip_monthly";
    public static final String YEARLY = "dc_vip_yearly";
    private final Activity activity;
    private final WebView webView;
    private BillingClient client;
    private volatile boolean ready = false;
    private volatile boolean vip = false;
    private final Map<String, ProductDetails> details = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, String> prices = Collections.synchronizedMap(new HashMap<>());

    public VipBillingManager(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
        client = BillingClient.newBuilder(activity)
            .setListener(this::onPurchasesUpdated)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build();
        client.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(BillingResult result) {
                ready = result.getResponseCode() == BillingClient.BillingResponseCode.OK;
                if (ready) { queryProducts(); refresh(); } else notifyWeb();
            }
            @Override public void onBillingServiceDisconnected() {
                ready = false;
                notifyWeb();
            }
        });
    }

    public Object javascriptBridge() { return new Bridge(); }

    private void queryProducts() {
        List<QueryProductDetailsParams.Product> requested = new ArrayList<>();
        requested.add(QueryProductDetailsParams.Product.newBuilder().setProductId(MONTHLY).setProductType(BillingClient.ProductType.SUBS).build());
        requested.add(QueryProductDetailsParams.Product.newBuilder().setProductId(YEARLY).setProductType(BillingClient.ProductType.SUBS).build());
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder().setProductList(requested).build();
        client.queryProductDetailsAsync(params, (result, response) -> {
            if (result.getResponseCode() != BillingClient.BillingResponseCode.OK || response == null) { notifyWeb(); return; }
            List<ProductDetails> list = response.getProductDetailsList();
            if (list != null) for (ProductDetails item : list) {
                details.put(item.getProductId(), item);
                List<ProductDetails.SubscriptionOfferDetails> offers = item.getSubscriptionOfferDetails();
                if (offers != null && !offers.isEmpty()) {
                    List<ProductDetails.PricingPhase> phases = offers.get(0).getPricingPhases().getPricingPhaseList();
                    if (phases != null && !phases.isEmpty()) prices.put(item.getProductId(), phases.get(phases.size()-1).getFormattedPrice());
                }
            }
            notifyWeb();
        });
    }

    private boolean isVipProduct(Purchase purchase) {
        List<String> ids = purchase.getProducts();
        return ids != null && (ids.contains(MONTHLY) || ids.contains(YEARLY));
    }

    private void onPurchasesUpdated(BillingResult result, List<Purchase> purchases) {
        if (result.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase purchase : purchases) handlePurchase(purchase);
        } else if (result.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
            toast("Purchase cancelled. No payment was taken.");
        } else if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            toast("Google Play could not complete the purchase. Please try again.");
        }
        refresh();
    }

    private void handlePurchase(Purchase purchase) {
        if (purchase.getPurchaseState() != Purchase.PurchaseState.PURCHASED || !isVipProduct(purchase)) {
            notifyWeb(); // Pending purchases never unlock VIP.
            return;
        }
        vip = true;
        if (!purchase.isAcknowledged() && client != null && ready) {
            AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.getPurchaseToken()).build();
            client.acknowledgePurchase(params, result -> {
                if (result.getResponseCode() != BillingClient.BillingResponseCode.OK)
                    toast("Purchase succeeded; acknowledgement will be retried.");
            });
        }
        notifyWeb();
    }

    public void refresh() {
        if (client == null || !ready) return;
        QueryPurchasesParams params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build();
        client.queryPurchasesAsync(params, (result, purchases) -> {
            if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) return;
            boolean found = false;
            if (purchases != null) for (Purchase purchase : purchases) {
                if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED && isVipProduct(purchase)) {
                    found = true;
                    handlePurchase(purchase);
                }
            }
            vip = found;
            notifyWeb();
        });
    }

    private void launch(String productId) {
        if (!MONTHLY.equals(productId) && !YEARLY.equals(productId)) { toast("Unknown VIP plan."); return; }
        if (!ready) { toast("Google Play billing is not connected yet."); return; }
        ProductDetails product = details.get(productId);
        if (product == null || product.getSubscriptionOfferDetails() == null || product.getSubscriptionOfferDetails().isEmpty()) {
            toast("VIP plans are not configured in Google Play Console yet.");
            return;
        }
        String offer = product.getSubscriptionOfferDetails().get(0).getOfferToken();
        BillingFlowParams.ProductDetailsParams line = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product).setOfferToken(offer).build();
        BillingFlowParams flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(Collections.singletonList(line)).build();
        BillingResult result = client.launchBillingFlow(activity, flow);
        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK)
            toast("Google Play checkout could not be opened. Please try again.");
    }

    private void notifyWeb() {
        boolean active = vip;
        webView.post(() -> webView.evaluateJavascript(
            "window.dispatchEvent(new CustomEvent('dc-vip-status',{detail:{vip:" + active + "}}));", null));
    }

    private void toast(String message) {
        activity.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_LONG).show());
    }

    public void close() {
        if (client != null) { client.endConnection(); client = null; }
    }

    private final class Bridge {
        @JavascriptInterface public boolean isVip() { return vip; }
        @JavascriptInterface public String getPrice(String id) {
            String value = prices.get(id);
            return value == null ? "" : value;
        }
        @JavascriptInterface public void launchSubscription(String id) {
            activity.runOnUiThread(() -> launch(id));
        }
        @JavascriptInterface public void restorePurchases() {
            activity.runOnUiThread(() -> {
                if (!ready) { toast("Google Play billing is unavailable."); return; }
                refresh(); queryProducts();
                toast("Checking purchases linked to your Google Play account.");
            });
        }
    }
}
