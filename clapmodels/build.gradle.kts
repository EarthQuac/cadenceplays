plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName.set("clapmodels")
    dynamicDelivery {
        deliveryType.set("on-demand")
    }
}

tasks.matching { it.name == "assetPackPreBundleTask" }.configureEach {
    dependsOn(rootProject.tasks.named("prepareClapModelAssets"))
}
