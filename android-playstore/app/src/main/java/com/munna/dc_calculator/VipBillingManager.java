package com.munna.dc_calculator;

import android.app.Activity;
import android.widget.Toast;
import com.android.billingclient.api.*;
import java.util.*;

/** Native Google Play Billing bridge. Products must be configured in Play Console. */
public final class VipBillingManager implements PurchasesUpdatedListener {
    public static final String MONTHLY = "dc_vip_monthly";
    public static final String YEARLY = "dc_vip_yearly";

    public interface StatusListener {
        void onVipStatusChanged(boolean active, String message);
    }

    private final Activity activity;
    private final StatusListener listener;
    private BillingClient client;
    private volatile boolean ready = false;
    private volatile boolean vip = false;
    private boolean firstStatusSent = false;
    private final Map<String, ProductDetails> products =
        Collections.synchronizedMap(new HashMap<>());
    private final Map<String, String> prices =
        Collections.synchronizedMap(new HashMap<>());

    public VipBillingManager(Activity activity, StatusListener listener) {
        this.activity = activity;
        this.listener = listener;
        client = BillingClient.newBuilder(activity)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build();
        connect();
    }

    private void connect() {
        if (client == null) return;
        client.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(BillingResult result) {
                ready = result.getResponseCode() == BillingClient.BillingResponseCode.OK;
                if (ready) {
                    queryProducts();
                    refresh();
                } else {
                    notifyStatus(vip, "Google Play Billing is unavailable right now.");
                }
            }
            @Override public void onBillingServiceDisconnected() {
                ready = false;
                notifyStatus(vip, "Google Play Billing disconnected. Reconnect and try again.");
            }
        });
    }

    public boolean isVipActive() { return vip; }

    public String getPrice(String productId) {
        String value = prices.get(productId);
        return value == null ? "" : value;
    }

    private void queryProducts() {
        if (client == null || !ready) return;
        List<QueryProductDetailsParams.Product> requested = new ArrayList<>();
        requested.add(QueryProductDetailsParams.Product.newBuilder()
            .setProductId(MONTHLY).setProductType(BillingClient.ProductType.SUBS).build());
        requested.add(QueryProductDetailsParams.Product.newBuilder()
            .setProductId(YEARLY).setProductType(BillingClient.ProductType.SUBS).build());
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
            .setProductList(requested).build();
        client.queryProductDetailsAsync(params, (result, response) -> {
            if (result.getResponseCode() != BillingClient.BillingResponseCode.OK || response == null) {
                notifyStatus(vip, "VIP products are not available in Google Play Console yet.");
                return;
            }
            List<ProductDetails> list = response.getProductDetailsList();
            if (list != null) {
                for (ProductDetails product : list) {
                    products.put(product.getProductId(), product);
                    List<ProductDetails.SubscriptionOfferDetails> offers = product.getSubscriptionOfferDetails();
                    if (offers != null && !offers.isEmpty()) {
                        List<ProductDetails.PricingPhase> phases =
                            offers.get(0).getPricingPhases().getPricingPhaseList();
                        if (phases != null && !phases.isEmpty()) {
                            prices.put(product.getProductId(),
                                phases.get(phases.size() - 1).getFormattedPrice());
                        }
                    }
                }
            }
            notifyStatus(vip, "Google Play Billing connected.");
        });
    }

    public void launchSubscription(String productId) {
        if (!MONTHLY.equals(productId) && !YEARLY.equals(productId)) {
            toast("Unknown VIP plan.");
            return;
        }
        if (!ready || client == null) {
            toast("Google Play Billing is not ready. Try again in a moment.");
            return;
        }
        ProductDetails product = products.get(productId);
        if (product == null || product.getSubscriptionOfferDetails() == null
                || product.getSubscriptionOfferDetails().isEmpty()) {
            toast("VIP plans must first be configured in Google Play Console. No payment was taken.");
            return;
        }
        String offerToken = product.getSubscriptionOfferDetails().get(0).getOfferToken();
        BillingFlowParams.ProductDetailsParams line = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product).setOfferToken(offerToken).build();
        BillingFlowParams flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(Collections.singletonList(line)).build();
        BillingResult result = client.launchBillingFlow(activity, flow);
        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            toast("Google Play checkout could not open. No payment was taken.");
        }
    }

    @Override public void onPurchasesUpdated(BillingResult result, List<Purchase> purchases) {
        if (result.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase purchase : purchases) handlePurchase(purchase);
        } else if (result.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
            toast("Purchase cancelled. No payment was taken.");
        } else if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            toast("Google Play could not complete the purchase. Please try again.");
        }
        refresh();
    }

    private boolean isVipProduct(Purchase purchase) {
        List<String> ids = purchase.getProducts();
        return ids != null && (ids.contains(MONTHLY) || ids.contains(YEARLY));
    }

    private void handlePurchase(Purchase purchase) {
        if (purchase.getPurchaseState() != Purchase.PurchaseState.PURCHASED || !isVipProduct(purchase)) return;
        boolean changed = !vip;
        vip = true;
        if (!purchase.isAcknowledged() && client != null && ready) {
            AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.getPurchaseToken()).build();
            client.acknowledgePurchase(params, result -> {
                if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                    toast("Purchase succeeded; Google Play acknowledgement may need retry.");
                }
            });
        }
        if (changed) notifyStatus(true, "VIP membership purchase found.");
    }

    public void refresh() {
        if (client == null || !ready) {
            notifyStatus(vip, "VIP status is not available yet.");
            return;
        }
        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS).build();
        client.queryPurchasesAsync(params, (result, purchases) -> {
            if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                notifyStatus(vip, "Could not refresh Google Play purchases.");
                return;
            }
            boolean found = false;
            if (purchases != null) {
                for (Purchase purchase : purchases) {
                    if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED && isVipProduct(purchase)) {
                        found = true;
                        handlePurchase(purchase);
                    }
                }
            }
            boolean changed = vip != found;
            vip = found;
            if (changed || !firstStatusSent) {
                notifyStatus(vip, vip ? "VIP membership is active." : "No active VIP subscription was found.");
            }
        });
    }

    private void notifyStatus(boolean active, String message) {
        firstStatusSent = true;
        if (listener != null) activity.runOnUiThread(() -> listener.onVipStatusChanged(active, message));
    }

    private void toast(String message) {
        activity.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_LONG).show());
    }

    public void close() {
        if (client != null) {
            client.endConnection();
            client = null;
        }
    }
}
