package org.ofdrw.converter;

import com.itextpdf.io.font.otf.Glyph;
import com.itextpdf.io.font.otf.GlyphLine;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.geom.AffineTransform;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.canvas.PdfCanvasConstants;
import com.itextpdf.kernel.pdf.colorspace.PdfDeviceCs;
import com.itextpdf.kernel.pdf.colorspace.PdfPattern;
import com.itextpdf.kernel.pdf.colorspace.PdfShading;
import com.itextpdf.kernel.pdf.extgstate.PdfExtGState;
import com.itextpdf.kernel.pdf.filespec.PdfFileSpec;
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject;
import com.itextpdf.kernel.pdf.xobject.PdfImageXObject;
import com.itextpdf.layout.Canvas;
import org.dom4j.Element;
import org.ofdrw.converter.font.FontWrapper;
import org.ofdrw.converter.point.PathPoint;
import org.ofdrw.converter.point.TextCodePoint;
import org.ofdrw.converter.utils.CommonUtil;
import org.ofdrw.converter.utils.PointUtil;
import org.ofdrw.converter.utils.StringUtils;
import org.ofdrw.core.annotation.pageannot.Annot;
import org.ofdrw.core.attachment.CT_Attachment;
import org.ofdrw.core.basicStructure.pageObj.layer.CT_Layer;
import org.ofdrw.core.basicStructure.pageObj.layer.PageBlockType;
import org.ofdrw.core.basicStructure.pageObj.layer.block.*;
import org.ofdrw.core.basicType.ST_Array;
import org.ofdrw.core.basicType.ST_Box;
import org.ofdrw.core.basicType.ST_Pos;
import org.ofdrw.core.basicType.ST_RefID;
import org.ofdrw.core.compositeObj.CT_VectorG;
import org.ofdrw.core.graph.pathObj.FillColor;
import org.ofdrw.core.graph.pathObj.StrokeColor;
import org.ofdrw.core.pageDescription.color.color.CT_AxialShd;
import org.ofdrw.core.pageDescription.color.color.CT_Color;
import org.ofdrw.core.pageDescription.drawParam.CT_DrawParam;
import org.ofdrw.core.signatures.appearance.StampAnnot;
import org.ofdrw.core.text.font.CT_Font;
import org.ofdrw.reader.OFDReader;
import org.ofdrw.reader.PageInfo;
import org.ofdrw.reader.ResourceLocator;
import org.ofdrw.reader.ResourceManage;
import org.ofdrw.reader.model.AnnotionEntity;
import org.ofdrw.reader.model.StampAnnotEntity;
import org.ofdrw.reader.tools.ImageUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.ofdrw.converter.utils.CommonUtil.converterDpi;

/**
 * pdf?????????
 *
 * @author dltech21
 * @since 2020.08.11
 */
public class ItextMaker {

    private final Map<String, FontWrapper<PdfFont>> fontCache = new HashMap<>();

    private final OFDReader ofdReader;
    /**
     * ???????????????
     * <p>
     * ????????????OFD?????????
     */
    private final ResourceManage resMgt;

    /**
     * ??????????????????????????????????????????????????????????????????
     */

    public ItextMaker(OFDReader ofdReader) throws IOException {
        this.ofdReader = ofdReader;
        this.resMgt = ofdReader.getResMgt();
    }

    /**
     * ofd?????????object??????pdf
     *
     * @param pdf      PDF????????????
     * @param pageInfo ????????????
     * @return PDF??????
     * @throws IOException ?????????????????????????????????
     */
    public PdfPage makePage(PdfDocument pdf, PageInfo pageInfo) throws IOException {
        ST_Box pageBox = pageInfo.getSize();
        double pageWidthPixel = converterDpi(pageBox.getWidth());
        double pageHeightPixel = converterDpi(pageBox.getHeight());
        PageSize pageSize = new PageSize((float) pageWidthPixel, (float) pageHeightPixel);
        PdfPage pdfPage = pdf.addNewPage(pageSize);

        pdfPage.setMediaBox(
                new Rectangle(
                        (float) converterDpi(pageBox.getTopLeftX()), (float) converterDpi(pageBox.getTopLeftY()),
                        (float) converterDpi(pageBox.getWidth()), (float) converterDpi(pageBox.getHeight())
                )
        );

        final List<AnnotionEntity> annotationEntities = ofdReader.getAnnotationEntities();
        final List<StampAnnotEntity> stampAnnots = ofdReader.getStampAnnots();
        PdfCanvas pdfCanvas = new PdfCanvas(pdfPage);
        // ???????????????????????????????????????????????????????????????????????????????????????ZOrder?????????
        List<CT_Layer> layerList = pageInfo.getAllLayer();
        // ?????? ????????? ??? ???????????????
        writeLayer(resMgt, pdfCanvas, layerList, pageBox, null);
        // ??????????????????
        writeStamp(pdf, pdfCanvas, pageInfo, stampAnnots);
        // ????????????
        writeAnnoAppearance(resMgt, pdfCanvas, pageInfo, annotationEntities, pageBox);
        return pdfPage;
    }
    /**
     * ????????????
     *
     * @param pdf
     * @param ofdReader
     * @throws IOException
     */
    public void addAttachments(PdfDocument pdf, OFDReader ofdReader) throws IOException {
        List<CT_Attachment> attachmentList = ofdReader.getAttachmentList();
        for (CT_Attachment attachment : attachmentList) {
            Path attFile = ofdReader.getAttachmentFile(attachment);
            byte[] fileBytes = Files.readAllBytes(attFile);
            String fileName = attFile.getFileName().toString();
            final String attachmentName = attachment.getAttachmentName();
            String displayFileName = StringUtils.isBlank(attachmentName) ? fileName :
                    attachmentName.concat(fileName.contains(".") ?
                            fileName.substring(fileName.lastIndexOf(".")) : "");
            PdfFileSpec fs = PdfFileSpec.createEmbeddedFileSpec(pdf, fileBytes, null, displayFileName,
                    null);
            pdf.addFileAttachment(displayFileName, fs);
        }
    }

    /**
     * ????????????
     *
     * @param pdf                  PDF?????????
     * @param pdfCanvas            ???????????????
     * @param parent               OFD????????????
     * @param stampAnnotEntityList ????????????
     * @throws IOException ??????????????????
     */
    private void writeStamp(PdfDocument pdf, PdfCanvas pdfCanvas,
                            PageInfo parent,
                            List<StampAnnotEntity> stampAnnotEntityList) throws IOException {
        String pageID = parent.getId().toString();
        for (StampAnnotEntity stampAnnotVo : stampAnnotEntityList) {
            List<StampAnnot> stampAnnots = stampAnnotVo.getStampAnnots();
            for (StampAnnot stampAnnot : stampAnnots) {
                if (!stampAnnot.getPageRef().toString().equals(pageID)) {
                    // ???????????????????????????
                    continue;
                }
                ST_Box pageBox = parent.getSize();
                ST_Box sealBox = stampAnnot.getBoundary();
                ST_Box clipBox = stampAnnot.getClip();

                if (stampAnnotVo.getImgType().equalsIgnoreCase("ofd")) {
                    // ?????????????????????OFD????????????
                    try (OFDReader sealOfdReader = new OFDReader(new ByteArrayInputStream(stampAnnotVo.getImageByte()))) {
                        ResourceManage sealResMgt = sealOfdReader.getResMgt();
                        for (PageInfo ofdPageVo : sealOfdReader.getPageList()) {
                            // ???????????????????????????????????????????????????????????????????????????????????????ZOrder?????????
                            List<CT_Layer> layerList = ofdPageVo.getAllLayer();
                            // ??????????????????
                            writeLayer(sealResMgt, pdfCanvas, layerList, pageBox, sealBox);
                            // ????????????
                            writeAnnoAppearance(sealResMgt, pdfCanvas,
                                    ofdPageVo,
                                    sealOfdReader.getAnnotationEntities(),
                                    pageBox);
                        }
                    }
                } else {
                    // ????????????????????????
                    writeSealImage(pdf, pdfCanvas, pageBox, stampAnnotVo.getImageByte(), sealBox, clipBox);
                }
            }
        }
    }

    /**
     * ?????? ??????
     *
     * @param resMgt    ???????????????
     * @param pdfCanvas Canvas?????????
     * @param layerList ??????
     * @param box       ????????????
     * @param sealBox   ????????????
     * @throws IOException ????????????
     */
    private void writeLayer(ResourceManage resMgt,
                            PdfCanvas pdfCanvas,
                            List<CT_Layer> layerList,
                            ST_Box box,
                            ST_Box sealBox) throws IOException {
        for (CT_Layer layer : layerList) {
            writePageBlock(resMgt,
                    pdfCanvas,
                    box,
                    sealBox,
                    layer.getPageBlocks(),
                    layer.getDrawParam(),
                    null,
                    null,
                    null,
                    null);
        }
    }

    private void writeAnnoAppearance(ResourceManage resMgt,
                                     PdfCanvas pdfCanvas,
                                     PageInfo pageInfo,
                                     List<AnnotionEntity> annotionEntities,
                                     ST_Box box) {
        String pageId = pageInfo.getId().toString();
        for (AnnotionEntity annotionEntity : annotionEntities) {
            List<Annot> annotList = annotionEntity.getAnnots();
            if (annotList == null) {
                continue;
            }
            if (!pageId.equalsIgnoreCase(annotionEntity.getPageId())) {
                continue;
            }
            for (Annot annot : annotList) {
                try{
                    List<PageBlockType> pageBlockTypeList = annot.getAppearance().getPageBlocks();
                    //?????????boundary
                    ST_Box annotBox = annot.getAppearance().getBoundary();
                    writePageBlock(resMgt, pdfCanvas, box, null, pageBlockTypeList, null, annotBox, null, null, null);
                } catch (Exception e) {
                    //    e.printStackTrace();
                }
            }
        }
    }

    /**
     * ?????? ??????
     *
     * @param resMgt                  ???????????????
     * @param pdfCanvas               Canvas?????????
     * @param box                     ????????????
     * @param sealBox                 ????????????
     * @param pageBlockTypeList       ?????????????????????
     * @param drawparam               ????????????ID
     * @param annotBox                ????????????
     * @param compositeObjectAlpha    ?????????
     * @param compositeObjectBoundary ??????????????????
     * @param compositeObjectCTM      ????????????????????????
     * @throws IOException ????????????
     * @throws IOException ??????????????????
     */
    private void writePageBlock(ResourceManage resMgt,
                                PdfCanvas pdfCanvas,
                                ST_Box box, ST_Box sealBox,
                                List<PageBlockType> pageBlockTypeList,
                                ST_RefID drawparam,
                                ST_Box annotBox,
                                Integer compositeObjectAlpha,
                                ST_Box compositeObjectBoundary,
                                ST_Array compositeObjectCTM) throws IOException {
        Color defaultStrokeColor = ColorConstants.BLACK;
        Color defaultFillColor = ColorConstants.BLACK;
        float defaultLineWidth = 0.353f;
        // ???????????????????????????
        CT_DrawParam ctDrawParam = null;
        if (drawparam != null) {
            ctDrawParam = resMgt.getDrawParamFinal(drawparam.toString());
        }
        if (ctDrawParam != null) {
            if (ctDrawParam.getLineWidth() != null) {
                defaultLineWidth = ctDrawParam.getLineWidth().floatValue();
            }
            if (ctDrawParam.getStrokeColor() != null) {
                defaultStrokeColor = ColorConvert.pdfRGB(resMgt, ctDrawParam.getStrokeColor());
            }
            if (ctDrawParam.getFillColor() != null) {
                defaultFillColor = ColorConvert.pdfRGB(resMgt, ctDrawParam.getFillColor());
            }
        }

        for (PageBlockType block : pageBlockTypeList) {
            if (block instanceof TextObject) {
                // text
                Color fillColor = defaultFillColor;
                TextObject textObject = (TextObject) block;
                int alpha = 255;
                final FillColor ctFillColor = textObject.getFillColor();
                if (ctFillColor != null) {
                    if (ctFillColor.getValue() != null) {
                        fillColor = ColorConvert.pdfRGB(resMgt, ctFillColor);
                    } else if (ctFillColor.getColorByType() != null) {
                        // todo
                        CT_AxialShd ctAxialShd = ctFillColor.getColorByType();
                        fillColor = ColorConvert.pdfRGB(resMgt, ctAxialShd.getSegments().get(0).getColor());
                    }
                    alpha = ctFillColor.getAlpha();
                }
                //TODO ??????annot???????????????????????????
                writeText(resMgt, pdfCanvas, box, sealBox, annotBox, textObject, fillColor, alpha, compositeObjectBoundary, compositeObjectCTM);
            } else if (block instanceof ImageObject) {
                ImageObject imageObject = (ImageObject) block;
                resMgt.superDrawParam(imageObject); // ??????????????????
                writeImage(resMgt, pdfCanvas, box, imageObject, annotBox, compositeObjectCTM);
            } else if (block instanceof PathObject) {
                PathObject pathObject = (PathObject) block;
                resMgt.superDrawParam(pathObject); // ??????????????????
                writePath(resMgt, pdfCanvas, box, sealBox, annotBox, pathObject, defaultFillColor, defaultStrokeColor, defaultLineWidth, compositeObjectAlpha, compositeObjectBoundary, compositeObjectCTM);
            } else if (block instanceof CompositeObject) {
                CompositeObject compositeObject = (CompositeObject) block;
                // ???????????????????????????
                CT_VectorG vectorG = resMgt.getCompositeGraphicUnit(compositeObject.getResourceID().toString());
                Integer currentCompositeObjectAlpha = compositeObject.getAlpha();
                ST_Box currentCompositeObjectBoundary = compositeObject.getBoundary();
                ST_Array currentCompositeObjectCTM = compositeObject.getCTM();
                writePageBlock(resMgt, pdfCanvas, box, sealBox, vectorG.getContent().getPageBlocks(), drawparam, annotBox, currentCompositeObjectAlpha, currentCompositeObjectBoundary, currentCompositeObjectCTM);
            } else if (block instanceof CT_PageBlock) {
                writePageBlock(resMgt, pdfCanvas, box, sealBox, ((CT_PageBlock) block).getPageBlocks(), drawparam, annotBox, compositeObjectAlpha, compositeObjectBoundary, compositeObjectCTM);
            }
        }
    }


    private void writePath(ResourceManage resMgt,
                           PdfCanvas pdfCanvas,
                           ST_Box box,
                           ST_Box sealBox,
                           ST_Box annotBox,
                           PathObject pathObject,
                           Color defaultFillColor,
                           Color defaultStrokeColor,
                           float defaultLineWidth,
                           Integer compositeObjectAlpha,
                           ST_Box compositeObjectBoundary,
                           ST_Array compositeObjectCTM) {
        pdfCanvas.saveState();
        CT_DrawParam ctDrawParam = resMgt.superDrawParam(pathObject);
        if (ctDrawParam != null) {
            // ???????????????????????????????????????
            if (pathObject.getStrokeColor() == null
                    && ctDrawParam.getStrokeColor() != null) {
                pathObject.setStrokeColor(new CT_Color().setValue(ctDrawParam.getStrokeColor().getValue()));
            }
            if (pathObject.getFillColor() == null
                    && ctDrawParam.getFillColor() != null) {
                pathObject.setStrokeColor(new CT_Color().setValue(ctDrawParam.getFillColor().getValue()));
            }
            if (pathObject.getLineWidth() == null
                    && ctDrawParam.getLineWidth() != null) {
                pathObject.setLineWidth(ctDrawParam.getLineWidth());
            }
        }

        if (pathObject.getStrokeColor() != null) {
            StrokeColor strokeColor = pathObject.getStrokeColor();
            if (strokeColor.getValue() != null) {
                pdfCanvas.setStrokeColor(ColorConvert.pdfRGB(resMgt, strokeColor));
            }
            Element e = strokeColor.getOFDElement("AxialShd");
            if (e != null) {
                CT_AxialShd ctAxialShd = new CT_AxialShd(e);
                final CT_Color startColor = ctAxialShd.getSegments().get(0).getColor();
                final CT_Color endColor = ctAxialShd.getSegments().get(ctAxialShd.getSegments().size() - 1).getColor();
                if (pathObject.getCTM() != null) {
                }
                PdfShading.Axial axial = new PdfShading.Axial(new PdfDeviceCs.Rgb(), 0, 0, ColorConvert.pdfRGB(resMgt, startColor).getColorValue(),
                        box.getWidth().floatValue(), box.getHeight().floatValue(), ColorConvert.pdfRGB(resMgt, endColor).getColorValue());
                PdfPattern.Shading shading = new PdfPattern.Shading(axial);
                pdfCanvas.setStrokeColorShading(shading);
                defaultStrokeColor = ColorConvert.pdfRGB(resMgt, endColor);
                pdfCanvas.setStrokeColor(defaultStrokeColor);
            }
        } else {
            pdfCanvas.setStrokeColor(defaultStrokeColor);
        }

        float lineWidth = pathObject.getLineWidth() != null ? pathObject.getLineWidth().floatValue() : defaultLineWidth;
        if (pathObject.getCTM() != null && pathObject.getLineWidth() != null) {
            Double[] ctm = pathObject.getCTM().toDouble();
            double a = ctm[0];
            double c = ctm[2];
            double sx = Math.signum(a) * Math.sqrt(a * a + c * c);
            lineWidth = (float) (lineWidth * sx);
        }
        if (pathObject.getStroke()) {
            if (pathObject.getDashPattern() != null) {
                float unitsOn = (float) converterDpi(pathObject.getDashPattern().toDouble()[0].floatValue());
                float unitsOff = (float) converterDpi(pathObject.getDashPattern().toDouble()[1].floatValue());
                float phase = (float) converterDpi(pathObject.getDashOffset().floatValue());
                pdfCanvas.setLineDash(unitsOn, unitsOff, phase);
            }
            pdfCanvas.setLineJoinStyle(pathObject.getJoin().ordinal());
            pdfCanvas.setLineCapStyle(pathObject.getCap().ordinal());
            pdfCanvas.setMiterLimit(pathObject.getMiterLimit().floatValue());
            path(pdfCanvas, box, sealBox, annotBox, pathObject, compositeObjectBoundary, compositeObjectCTM);

//            System.out.println(pathObject.getLineWidth()+"="+lineWidth+"-"+converterDpi(lineWidth)+"-"+defaultLineWidth);

            //TODO ???????????????????????????
            if (compositeObjectBoundary != null) {
                pdfCanvas.setLineWidth(lineWidth);
            } else {
                pdfCanvas.setLineWidth((float) converterDpi(lineWidth));
            }

            pdfCanvas.stroke();
            pdfCanvas.restoreState();
        }
        if (pathObject.getFill()) {
            pdfCanvas.saveState();
            if (compositeObjectAlpha != null) {
                PdfExtGState gs1 = new PdfExtGState();
                gs1.setFillOpacity(compositeObjectAlpha * 1.0f / 255f);
                pdfCanvas.setExtGState(gs1);
            }
            FillColor fillColor = (FillColor) pathObject.getFillColor();
            if (fillColor != null) {
                if (fillColor.getValue() != null) {
                    pdfCanvas.setFillColor(ColorConvert.pdfRGB(resMgt, fillColor));
                }
                Element e = fillColor.getOFDElement("AxialShd");
                if (e != null) {
                    CT_AxialShd ctAxialShd = new CT_AxialShd(e);
                    final CT_Color endColor = ctAxialShd.getSegments().get(ctAxialShd.getSegments().size() - 1).getColor();
                    defaultFillColor = ColorConvert.pdfRGB(resMgt, endColor);
                    pdfCanvas.setFillColor(defaultFillColor);
                }
            } else {
                pdfCanvas.setFillColor(defaultFillColor);
            }if(sealBox ==null){
                //  System.out.println(box);
                path(pdfCanvas, box.getInstance("0 0 0 0"), sealBox, annotBox, pathObject, compositeObjectBoundary, compositeObjectCTM);
            }else {
                path(pdfCanvas, box, sealBox, annotBox, pathObject, compositeObjectBoundary, compositeObjectCTM);

            }
            pdfCanvas.fill();
            pdfCanvas.restoreState();
        }
    }

    private void path(PdfCanvas pdfCanvas, ST_Box box, ST_Box sealBox, ST_Box annotBox, PathObject pathObject, ST_Box compositeObjectBoundary, ST_Array compositeObjectCTM) {
        if (pathObject.getBoundary() == null) {
            return;
        }
        if (sealBox != null) {
            pathObject.setBoundary(pathObject.getBoundary().getTopLeftX() + sealBox.getTopLeftX(),
                    pathObject.getBoundary().getTopLeftY() + sealBox.getTopLeftY(),
                    pathObject.getBoundary().getWidth(),
                    pathObject.getBoundary().getHeight());
        }
        if (annotBox != null) {
            //TODO ??????annot?????????????????????
            pathObject.setBoundary(
                    pathObject.getBoundary().getTopLeftX() + converterDpi(annotBox.getTopLeftX()),
                    pathObject.getBoundary().getTopLeftY() + converterDpi(annotBox.getTopLeftY()),
                    pathObject.getBoundary().getWidth(),
                    pathObject.getBoundary().getHeight());
        }
        List<PathPoint> listPoint = PointUtil.calPdfPathPoint(box.getWidth(), box.getHeight(), pathObject.getBoundary(), PointUtil.convertPathAbbreviatedDatatoPoint(pathObject.getAbbreviatedData()), pathObject.getCTM() != null, pathObject.getCTM(), compositeObjectBoundary, compositeObjectCTM, true);
        for (PathPoint pathPoint : listPoint) {
            switch (pathPoint.type) {
                case "M":
                case "S":
                    pdfCanvas.moveTo(pathPoint.x1, pathPoint.y1);
                    break;
                case "L":
                    pdfCanvas.lineTo(pathPoint.x1, pathPoint.y1);
                    break;
                case "B":
                    pdfCanvas.curveTo(pathPoint.x1, pathPoint.y1,
                            pathPoint.x2, pathPoint.y2,
                            pathPoint.x3, pathPoint.y3);
                    break;
                case "Q":
                    pdfCanvas.curveTo(pathPoint.x1, pathPoint.y1,
                            pathPoint.x2, pathPoint.y2);
                    break;
                case "C":
                    pdfCanvas.closePath();
                    break;
            }
        }
    }

    private void writeImage(ResourceManage resMgt, PdfCanvas pdfCanvas, ST_Box box, ImageObject imageObject, ST_Box annotBox, ST_Array compositeObjectCTM) throws IOException {
        final ST_RefID resourceID = imageObject.getResourceID();
        if (resourceID == null){
            return;
        }
        byte[] imageByteArray = resMgt.getImageByteArray(resourceID.toString());
        if (imageByteArray == null) {
            return;
        }
        pdfCanvas.saveState();

        PdfImageXObject pdfImageObject = new PdfImageXObject(ImageDataFactory.create(imageByteArray));
        if (annotBox != null) {
            float x = annotBox.getTopLeftX().floatValue();
            float y = box.getHeight().floatValue() - (annotBox.getTopLeftY().floatValue() + annotBox.getHeight().floatValue());
            float width = annotBox.getWidth().floatValue();
            float height = annotBox.getHeight().floatValue();
            Rectangle rect = new Rectangle((float) converterDpi(x), (float) converterDpi(y), (float) converterDpi(width), (float) converterDpi(height));
            pdfCanvas.addXObject(pdfImageObject, rect);
        } else {
            org.apache.pdfbox.util.Matrix matrix = CommonUtil.toPFMatrix(CommonUtil.getImageMatrixFromOfd(imageObject, box, compositeObjectCTM));
            float a = matrix.getValue(0, 0);
            float b = matrix.getValue(0, 1);
            float c = matrix.getValue(1, 0);
            float d = matrix.getValue(1, 1);
            float e = matrix.getValue(2, 0);
            float f = matrix.getValue(2, 1);
            pdfCanvas.addXObject(pdfImageObject, a, b, c, d, e, f);
        }
        pdfCanvas.restoreState();
    }

    private void writeSealImage(PdfDocument pdfDocument, PdfCanvas pdfCanvas, ST_Box box, byte[] image, ST_Box sealBox, ST_Box clipBox) throws IOException {
        if (image == null) {
            return;
        }
        float x = sealBox.getTopLeftX().floatValue();
        float y = box.getHeight().floatValue() - (sealBox.getTopLeftY().floatValue() + sealBox.getHeight().floatValue());
        float width = sealBox.getWidth().floatValue();
        float height = sealBox.getHeight().floatValue();
        Rectangle rect = new Rectangle((float) converterDpi(x), (float) converterDpi(y), (float) converterDpi(width), (float) converterDpi(height));
        // ?????????????????????????????????????????? 244????????????????????????
        BufferedImage bImg = ImageUtils.clearWhiteBackground(ImageIO.read(new ByteArrayInputStream(image)), 244);
        ImageData img = ImageDataFactory.create(bImg, null);

        PdfFormXObject xObject = new PdfFormXObject(new Rectangle(rect.getWidth(), rect.getHeight()));
        PdfCanvas xObjectCanvas = new PdfCanvas(xObject, pdfDocument);
        if (clipBox != null) {
            xObjectCanvas.rectangle(converterDpi(clipBox.getTopLeftX()), rect.getHeight() - (converterDpi(clipBox.getTopLeftY()) + converterDpi(clipBox.getHeight())), converterDpi(clipBox.getWidth()), converterDpi(clipBox.getHeight()));
            xObjectCanvas.clip();
            xObjectCanvas.endPath();
        }
        xObjectCanvas.addImage(img, rect.getWidth(), 0, 0, rect.getHeight(), 0, 0);
        com.itextpdf.layout.element.Image clipped = new com.itextpdf.layout.element.Image(xObject);
        Canvas canvas = new Canvas(pdfCanvas, pdfDocument, rect);
        canvas.add(clipped);
        canvas.close();
    }

    private void writeText(ResourceManage resMgt, PdfCanvas pdfCanvas, ST_Box box, ST_Box sealBox, ST_Box annotBox, TextObject textObject, Color fillColor, int alpha, ST_Box compositeObjectBoundary, ST_Array compositeObjectCTM) {
        float fontSize = textObject.getSize().floatValue();
        pdfCanvas.setFillColor(fillColor);
        if (textObject.getFillColor() != null) {
            Element e = textObject.getFillColor().getOFDElement("AxialShd");
            if (e != null) {
                CT_AxialShd ctAxialShd = new CT_AxialShd(e);

                final CT_Color startColor = ctAxialShd.getSegments().get(0).getColor();
                final CT_Color endColor = ctAxialShd.getSegments().get(ctAxialShd.getSegments().size() - 1).getColor();

                ST_Pos startPos = ctAxialShd.getStartPoint();
                ST_Pos endPos = ctAxialShd.getEndPoint();
                double x1 = startPos.getX(), y1 = startPos.getY();
                double x2 = endPos.getX(), y2 = endPos.getY();
                if (textObject.getCTM() != null) {
                    double[] newPoint = PointUtil.ctmCalPoint(startPos.getX(), startPos.getY(), textObject.getCTM().toDouble());
                    x1 = newPoint[0];
                    y1 = newPoint[1];
                    newPoint = PointUtil.ctmCalPoint(x2, y2, textObject.getCTM().toDouble());
                    x2 = newPoint[0];
                    y2 = newPoint[1];
                }
                double[] realPos = PointUtil.adjustPos(box.getWidth(), box.getHeight(), x1, y1, textObject.getBoundary());
                x1 = realPos[0];
                y1 = box.getHeight() - realPos[1];
                realPos = PointUtil.adjustPos(box.getWidth(), box.getHeight(), x2, y2, textObject.getBoundary());
                x2 = realPos[0];
                y2 = box.getHeight() - realPos[1];
                PdfShading.Axial axial = new PdfShading.Axial(new PdfDeviceCs.Rgb(), (float) x1, (float) y1, ColorConvert.pdfRGB(resMgt, startColor).getColorValue(),
                        (float) x2, (float) y2, ColorConvert.pdfRGB(resMgt, endColor).getColorValue());
                PdfPattern.Shading shading = new PdfPattern.Shading(axial);
                pdfCanvas.setFillColorShading(shading);
            }
        }
        PdfExtGState gs1 = new PdfExtGState();
        gs1.setFillOpacity(alpha / 255f);
        pdfCanvas.setExtGState(gs1);

        if (sealBox != null && textObject.getBoundary() != null) {
            textObject.setBoundary(textObject.getBoundary().getTopLeftX() + sealBox.getTopLeftX(),
                    textObject.getBoundary().getTopLeftY() + sealBox.getTopLeftY(),
                    textObject.getBoundary().getWidth(),
                    textObject.getBoundary().getHeight());
        }
        if (annotBox != null && textObject.getBoundary() != null) {
            textObject.setBoundary(textObject.getBoundary().getTopLeftX() + annotBox.getTopLeftX(),
                    textObject.getBoundary().getTopLeftY() + annotBox.getTopLeftY(),
                    textObject.getBoundary().getWidth(),
                    textObject.getBoundary().getHeight());
        }
        //start:roy19831015@gmail.com ??????????????????ctm???????????????
        double lineWidth = 0.0;
        if (textObject.getLineWidth() != null) {
            lineWidth = textObject.getLineWidth();
        }
        if (textObject.getCTM() != null) {
            Double[] ctm = textObject.getCTM().toDouble();
            double a = ctm[0];
            double b = ctm[1];
            double c = ctm[2];
            double d = ctm[3];
            double sx = a > 0 ? Math.signum(a) * Math.sqrt(a * a + c * c) : Math.sqrt(a * a + c * c);
            double angel = Math.atan2(-b, d);
            if (!(angel == 0 && a != 0 && d == 1)) {
                fontSize = (float) (fontSize * sx);
                lineWidth = lineWidth * sx;
            }
        }
        if (compositeObjectCTM != null) {
            Double[] ctm = compositeObjectCTM.toDouble();
            double a = ctm[0];
            double b = ctm[1];
            double c = ctm[2];
            double d = ctm[3];
            double sx = a > 0 ? Math.signum(a) * Math.sqrt(a * a + c * c) : Math.sqrt(a * a + c * c);
            double angel = Math.atan2(-b, d);
            if (!(angel == 0 && a != 0 && d == 1)) {
                fontSize = (float) (fontSize * sx);
                lineWidth = lineWidth * sx;
            }
        }

        // ????????????
        CT_Font ctFont = resMgt.getFont(textObject.getFont().toString());
        FontWrapper<PdfFont> pdfFontWrapper = getFont(resMgt.getOfdReader().getResourceLocator(), ctFont);
        PdfFont font = pdfFontWrapper.getFont();

        List<TextCodePoint> textCodePointList = PointUtil.calPdfTextCoordinate(box.getWidth(), box.getHeight(), textObject.getBoundary(), fontSize, textObject.getTextCodes(), textObject.getCGTransforms(), compositeObjectBoundary, compositeObjectCTM, textObject.getCTM() != null, textObject.getCTM(), true);
        double rx = 0, ry = 0;
        for (int i = 0; i < textCodePointList.size(); i++) {
            TextCodePoint textCodePoint = textCodePointList.get(i);
            if (i == 0) {
                rx = textCodePoint.x;
                ry = textCodePoint.y;
            }
            pdfCanvas.saveState();
            pdfCanvas.beginText();
            if (textObject.getMiterLimit() > 0)
                pdfCanvas.setMiterLimit(textObject.getMiterLimit().floatValue());

            pdfCanvas.moveText((textCodePoint.getX()), (textCodePoint.getY()));
            if (textObject.getCTM() != null) {
                Double[] ctm = textObject.getCTM().toDouble();
                double a = ctm[0];
                double b = ctm[1];
                double d = ctm[3];
                AffineTransform transform = new AffineTransform();
                double angel = Math.atan2(-b, d);
                transform.rotate(angel, rx, ry);
                pdfCanvas.concatMatrix(transform);
                if (angel == 0 && a != 0 && d == 1) {
                    textObject.setHScale(a);
                }
            }
            if (textObject.getHScale().floatValue() < 1) {
                AffineTransform transform = new AffineTransform();
                transform.setTransform(textObject.getHScale().floatValue(), 0, 0, 1, (1 - textObject.getHScale().floatValue()) * textCodePoint.getX(), 0);
                pdfCanvas.concatMatrix(transform);
            }
            pdfCanvas.setFontAndSize(font, (float) converterDpi(fontSize));

            if (textObject.getLineWidth() != null) {
//                pdfCanvas.setLineWidth((float) converterDpi(textObject.getLineWidth()));
                pdfCanvas.setLineWidth((float) lineWidth);
                pdfCanvas.setLineWidth(new Float(textObject.getLineWidth()));
                //??????????????????
                pdfCanvas.setFillColor(ColorConstants.BLACK);
                pdfCanvas.setTextRenderingMode(PdfCanvasConstants.TextRenderingMode.FILL_STROKE);
            }

            if (!StringUtils.isBlank(textCodePoint.getGlyph()) && !pdfFontWrapper.isEnableSimilarFontReplace()) {
                List<Glyph> glyphs = new ArrayList<>();

                 String[] glys = textCodePoint.getGlyph().split(" ");
                 for (String gly : glys) {
                 Glyph g = font.getFontProgram().getGlyphByCode(Integer.parseInt(gly));   //??????????????????
                 if (g != null) {
                 glyphs.add(g);
                 }
                 }
                if (glyphs.size() > 0) {
                    pdfCanvas.showText(new GlyphLine(glyphs));
                } else {
                    pdfCanvas.showText(textCodePoint.getText());
                }
            } else {
                pdfCanvas.showText(textCodePoint.getText());
            }

            pdfCanvas.endText();
            pdfCanvas.restoreState();
        }
    }

    /**
     * ????????????
     *
     * @param rl     ???????????????
     * @param ctFont ????????????
     * @return ??????
     */
    private FontWrapper<PdfFont> getFont(ResourceLocator rl, CT_Font ctFont) {
        String key = String.format("%s_%s_%s", ctFont.getFamilyName(), ctFont.attributeValue("FontName"), ctFont.getFontFile());
        if (fontCache.containsKey(key)) {
            return fontCache.get(key);
        }
        FontWrapper<PdfFont> font = FontLoader.getInstance().loadPDFFontSimilar(rl, ctFont);
        fontCache.put(key, font);
        return font;
    }
}
