/* 
 Tile grid analysis for tumor coverage

 Context:
 This script is designed for whole-slide images (WSI) opened in QuPath.
 Tumor regions have already been identified and stored as annotations
 (class = "Tumor") using a pixel classifier or manual annotation.

 What the script does:
 1. Divides the whole image into a grid of square tiles of fixed size
    (here: 100 µm x 100 µm, converted internally into pixels).
 2. For each tile, calculates how much of its area is covered by Tumor annotations.
 3. If a tile overlaps a tumor, the tile is given the class "TumorTile"
    and a measurement "TumorAreaPercent" (percentage of tile area covered by tumor).
 4. Tiles with no overlap are removed from the hierarchy.
 5. Original Tumor annotations are preserved. Their area (µm²) can also be added.


 Notes:
 - Only annotations with class "Tumor" are considered for overlap.
 - This script works on 2D images (no Z-stack / no timepoints).

 */


import qupath.lib.objects.PathObjects
import qupath.lib.roi.ROIs
import qupath.lib.regions.ImagePlane

// size of the tiles
double tileSizeMicrons = 100.0  // Tile size in microns (e.g. 100 µm x 100 µm)

// Get image and pixel size information
def imageData = getCurrentImageData()
def imageServer = imageData.getServer()
def pixelCalibration = imageServer.getPixelCalibration()
def imageName = imageData.getServer().getMetadata().getName()

// Convert tile size from microns to pixels because QuPath works internally in pixel
// We add 0.5 to round to the nearest whole number

int tileWidthPixels = (int)(tileSizeMicrons / pixelCalibration.pixelWidthMicrons + 0.5)
int tileHeightPixels = (int)(tileSizeMicrons / pixelCalibration.pixelHeightMicrons + 0.5)

int imageWidth = imageServer.getWidth()
int imageHeight = imageServer.getHeight()
def imagePlane = ImagePlane.getDefaultPlane()

// Create a regular grid of square tiles
def tileAnnotations = []

for (int y = 0; y < imageHeight; y += tileHeightPixels) {
    for (int x = 0; x < imageWidth; x += tileWidthPixels) {
        def tileROI = ROIs.createRectangleROI(x, y, tileWidthPixels, tileHeightPixels, imagePlane)
        def tileAnnotation = PathObjects.createAnnotationObject(tileROI)
        tileAnnotations << tileAnnotation
    }
}
addObjects(tileAnnotations)

//  Find tumor annotations
def tumorAnnotations = getAnnotationObjects().findAll {
    it.getPathClass()?.toString() == "Tumor"
}

//For each tile, measure tumor coverage (area overlap)
tileAnnotations.each { tile ->
    def tileArea = tile.getROI().getArea()
    double tumorAreaInTile = 0.0

    tumorAnnotations.each { tumor ->
        def intersection = tile.getROI().getGeometry().intersection(tumor.getROI().getGeometry())
        if (!intersection.empty)
            tumorAreaInTile += intersection.getArea()
    }

    if (tumorAreaInTile > 0) {
        tile.setPathClass(getPathClass("TumorTile"))
        tile.getMeasurementList().putMeasurement("TumorAreaPercent", 100.0 * tumorAreaInTile / tileArea)
    }
}

// Remove annotations without class
def unclassifiedAnnotations = getAnnotationObjects().findAll {
    it.isAnnotation() && it.getPathClass() == null
}
removeObjects(unclassifiedAnnotations, true)


print "Done: " + imageName
