/*
August  2025, Qupath version compatible 0.5

@author Alexandre Hego
 
 contact: alexandre.hego@uliege.be
 GIGA Cell imaging facility
 University of Liege 

 **************************************************************************
 Goal: Export the probability maps of one chosen class from multiple pixel classifiers
 into a single 3D OME-TIFF with multiple channels (one channel per classifier).

 **************************************************************************

 How it works:
1) For each classifier, create a prediction server (one channel per class).
2) Added automatic creation of a "probability_images" subfolder inside the project folder.
3) Extract ONLY the target class channel -> get a single-channel server (keeps Z/T).
4) Concatenate all single-channel servers into one multi-channel server (no fusion).
5) Write the result as a 3D OME-TIFF.
 **************************************************************************

Tutorial :
change the name of your pixel classifier and class in part 1) 
  **************************************************************************
 */

/* 0) import libraries and setup
****************************************************/
import qupath.lib.common.GeneralTools
import qupath.lib.images.servers.TransformedServerBuilder
import qupath.opencv.ml.pixel.PixelClassifierTools

// Setup & image info
def imageData  = getCurrentImageData()
def baseServer = imageData.getServer()
def baseName   = GeneralTools.getNameWithoutExtension(baseServer.getMetadata().getName())

//Create output directory inside project folder
def outputDir = buildFilePath(PROJECT_BASE_DIR, "probability_images")
def outputFile = new File(outputDir)

/* 1) Configure your classifiers & classes to export
****************************************************/

//  classifierName : exact name of the pixel classifier saved in QuPath
//  className      : exact class (channel) to export from that classifier

def classifiersToExport = [
    [classifierName: "classifier1", className: "class1"],
    [classifierName: "classifier2", className: "class2"],
    [classifierName: "classifier3", className: "class3"]
]


/* 2) Collect single-class servers
****************************************************/

def singleClassServers = []
def channelLabels      = []

classifiersToExport.each { entry ->
    def clf = loadPixelClassifier(entry.classifierName)
    if (clf == null) {
        print "[ERROR] Classifier not found: ${entry.classifierName}"
        return
    }

    def pred = PixelClassifierTools.createPixelClassificationServer(imageData, clf)
    def chans = pred.getMetadata().getChannels()
    int idx = chans.findIndexOf { it.getName() == entry.className }

    if (idx < 0) {
        print "[ERROR] Class '${entry.className}' not found in classifier '${entry.classifierName}'. " +
              "Available: ${chans*.getName()}"
        return
    }

    def one = new TransformedServerBuilder(pred).extractChannels(idx).build()
    singleClassServers << one
    channelLabels      << "${entry.classifierName}_${entry.className}"
    print "[OK] Added ${entry.classifierName}:${entry.className}"
}

if (singleClassServers.isEmpty()) {
    print "[ERROR] No channel extracted – check names."
    return
}

/* 3) Concatenate into multi-channel server
****************************************************/
def builder = new TransformedServerBuilder(singleClassServers[0])
(1..<singleClassServers.size()).each { i ->
    builder = builder.concatChannels(singleClassServers[i])
}
def multi = builder.build()

/* 4) Write output in "probability_images" folder
****************************************************/
def outName = "${baseName}_probs.ome.tif"
def outPath = buildFilePath(outputDir, outName)
writeImage(multi, outPath)

print "******************************************"
print "Done image: ${outName}"
