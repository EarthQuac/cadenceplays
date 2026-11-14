plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName.set("clapmodels")
    dynamicDelivery {
        deliveryType.set("on-demand")
    }
}

tasks.named("preBuild").configure {
    dependsOn(rootProject.tasks.named("prepareClapModelAssets"))
}
