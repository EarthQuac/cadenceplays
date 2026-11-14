plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName.set("clapmodels")
    dynamicDelivery {
        deliveryType.set("on-demand")
    }
}

