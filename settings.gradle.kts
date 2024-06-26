rootProject.name = "zoomimage"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("libs.versions.toml"))
        }
    }
}

/*
 * Release
 */
include(":zoomimage-compose")
include(":zoomimage-compose-coil")
include(":zoomimage-compose-coil-core")
include(":zoomimage-compose-glide")
include(":zoomimage-compose-sketch")
include(":zoomimage-compose-sketch3")
include(":zoomimage-compose-sketch3-core")
include(":zoomimage-compose-sketch-core")
include(":zoomimage-core")
include(":zoomimage-core-coil")
include(":zoomimage-core-glide")
include(":zoomimage-core-picasso")
include(":zoomimage-core-sketch")
include(":zoomimage-core-sketch3")
include(":zoomimage-view")
include(":zoomimage-view-coil")
include(":zoomimage-view-glide")
include(":zoomimage-view-picasso")
include(":zoomimage-view-sketch")   // TODO add zoomimage-view-sketch-core
include(":zoomimage-view-sketch3")


/*
 * Private
 */
include(":sample")
include(":internal:images")
include(":internal:test-utils")