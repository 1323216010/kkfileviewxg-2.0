package org.ofdrw.converter;

import org.apache.pdfbox.pdmodel.graphics.blend.BlendComposite;
import org.apache.pdfbox.pdmodel.graphics.blend.BlendMode;
import org.ofdrw.converter.font.FontWrapper;
import org.ofdrw.converter.font.GlyphData;
import org.ofdrw.converter.font.TrueTypeFont;
import org.ofdrw.converter.point.Tuple2;
import org.ofdrw.converter.utils.CommonUtil;
import org.ofdrw.converter.utils.MatrixUtils;
import org.ofdrw.converter.utils.StringUtils;
import org.ofdrw.core.annotation.pageannot.Annot;
import org.ofdrw.core.annotation.pageannot.Appearance;
import org.ofdrw.core.basicStructure.pageObj.layer.CT_Layer;
import org.ofdrw.core.basicStructure.pageObj.layer.PageBlockType;
import org.ofdrw.core.basicStructure.pageObj.layer.block.*;
import org.ofdrw.core.basicType.ST_Array;
import org.ofdrw.core.basicType.ST_Box;
import org.ofdrw.core.basicType.ST_RefID;
import org.ofdrw.core.compositeObj.CT_VectorG;
import org.ofdrw.core.graph.pathObj.AbbreviatedData;
import org.ofdrw.core.graph.pathObj.OptVal;
import org.ofdrw.core.pageDescription.CT_GraphicUnit;
import org.ofdrw.core.pageDescription.color.color.CT_Color;
import org.ofdrw.core.pageDescription.color.colorSpace.CT_ColorSpace;
import org.ofdrw.core.pageDescription.color.colorSpace.OFDColorSpaceType;
import org.ofdrw.core.pageDescription.drawParam.CT_DrawParam;
import org.ofdrw.core.signatures.appearance.StampAnnot;
import org.ofdrw.core.text.CT_CGTransform;
import org.ofdrw.core.text.TextCode;
import org.ofdrw.core.text.font.CT_Font;
import org.ofdrw.reader.OFDReader;
import org.ofdrw.reader.PageInfo;
import org.ofdrw.reader.ResourceManage;
import org.ofdrw.reader.model.AnnotionEntity;
import org.ofdrw.reader.model.StampAnnotEntity;
import org.ofdrw.reader.tools.ImageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ujmp.core.Matrix;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * AWT???????????????
 *
 * @author qaqtutu
 * @since 2021-05-06 23:00:01
 */
public abstract class AWTMaker {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());


    private OFDReader reader;

    /**
     * ?????????????????????(Pixels per millimeter)
     * <p>
     * ???????????? 7.874015748031496 ppm (???200 dpi???
     */
    protected double ppm = CommonUtil.dpiToPpm(200);

    public final Config config = new Config();

    /**
     * ????????????????????????????????????
     */
    protected boolean isStamp = false;


    protected List<PageInfo> pages;

    private final ResourceManage resourceManage;
    /**
     * ????????????????????????
     * <p>
     * ???????????????????????????????????????????????????
     * <p>
     * KEY: ?????????_?????????_????????????
     */
    private final Map<String, FontWrapper<TrueTypeFont>> fontCache = new HashMap<>();

    /**
     * ??????????????????????????????
     * <p>
     * OFD????????????????????????????????????
     * <p>
     * ???????????????????????????????????????????????? {@link #AWTMaker(org.ofdrw.reader.OFDReader, double)}
     *
     * @param reader OFD?????????
     * @param ppm    ?????????????????????(Pixels per millimeter)
     */
    public AWTMaker(OFDReader reader, int ppm) {
        this.reader = reader;
        this.resourceManage = reader.getResMgt();
        this.pages = reader.getPageList();
        if (this.ppm > 0) {
            this.ppm = ppm;
        }
    }

    /**
     * ??????????????????????????????
     * <p>
     * OFD????????????????????????????????????
     *
     * @param reader OFD?????????
     * @param ppm    ?????????????????????(Pixels per millimeter)???DPI???PPM??????????????????{@link CommonUtil#dpiToPpm(int)}???
     * @author iandjava
     */
    public AWTMaker(OFDReader reader, double ppm) {
        this.reader = reader;
        this.resourceManage = reader.getResMgt();
        this.pages = reader.getPageList();
        if (this.ppm > 0) {
            this.ppm = ppm;
        }
    }

    public int pageSize() {
        return pages.size();
    }

    private void writeStampAnnot(Graphics2D graphics, StampAnnotEntity stampAnnotVo, StampAnnot stampAnnot) {
        BufferedImage stampImage = null;
        graphics = (Graphics2D) graphics.create();
        try (ByteArrayInputStream inputStream = stampAnnotVo.getImageStream()) {
            if (stampAnnotVo.getImgType().equals("ofd")) {
                ImageMaker imageMaker = new ImageMaker(new OFDReader(inputStream), ppm);
                imageMaker.isStamp = true;
                imageMaker.config.setDrawBoundary(config.drawBoundary);
                if (imageMaker.pageSize() > 0) {
                    logger.debug("??????ofd????????????");
                    stampImage = imageMaker.makePage(0);
                }
            } else {
                stampImage = ImageIO.read(inputStream);
            }
            if (stampImage != null) {
                if (config.clearStampBackground) {
                    stampImage = ImageUtils.clearWhiteBackground(stampImage, config.stampBackgroundGray);
                }

                ST_Box stBox = stampAnnot.getBoundary();
                ST_Box clip = stampAnnot.getClip();
                Matrix m = MatrixUtils.base();
                graphics.setTransform(MatrixUtils.createAffineTransform(m));

                // ????????????
                final double fx = stBox.getWidth() / stampImage.getWidth();
                final double fy = stBox.getHeight() / stampImage.getHeight();
                m = MatrixUtils.scale(m, fx, fy);
                // ???????????????????????????
                m = MatrixUtils.move(m, stBox.getTopLeftX(), stBox.getTopLeftY());
                // ????????????
                m = MatrixUtils.scale(m, ppm, ppm);

                if (clip != null) {
                    Matrix m1 = MatrixUtils.base();
                    m1 = MatrixUtils.scale(m1, fx, fy);
                    m1 = MatrixUtils.move(m1, stBox.getTopLeftX() + clip.getTopLeftX(), stBox.getTopLeftY() + clip.getTopLeftY());
                    m1 = MatrixUtils.scale(m1, ppm, ppm);
                    graphics.setClip((int) m1.getAsDouble(2, 0), (int) m1.getAsDouble(2, 1),
                            (int) (stampImage.getWidth() * m1.getAsDouble(0, 0) * (clip.getWidth() / stBox.getWidth())),
                            (int) (stampImage.getHeight() * m1.getAsDouble(1, 1) * (clip.getHeight() / stBox.getHeight())));
                }
                graphics.setComposite(getStampComposite());
                graphics.drawImage(stampImage, MatrixUtils.createAffineTransform(m), null);
            }
        } catch (Exception e) {
            logger.error("??????????????????", e);
        } finally {
            graphics.dispose();
        }
    }

    /**
     * ??????????????????
     *
     * @return ????????????????????????
     */
    protected Composite getStampComposite() {
        // ????????????
        return BlendComposite.getInstance(BlendMode.MULTIPLY, 1);
    }

    /**
     * ????????????
     *
     * @param graphics ?????????????????????
     * @param pageInfo ????????????
     * @param matrix   ????????????
     */
    protected void writePage(Graphics2D graphics, PageInfo pageInfo, Matrix matrix) {
        // ???????????????????????????????????????????????????????????????????????????????????????ZOrder?????????
        final List<CT_Layer> layerList = pageInfo.getAllLayer();
        for (CT_Layer layer : layerList) {
            writeContent(graphics, layer, null, matrix);
        }

        final String pageId = pageInfo.getId().toString();
        // ????????????????????????
        for (StampAnnotEntity stampAnnotEntity : reader.getStampAnnots()) {
            List<StampAnnot> stampAnnots = stampAnnotEntity.getStampAnnots();
            for (StampAnnot stampAnnot : stampAnnots) {
                if (stampAnnot.getPageRef().toString().equals(pageId)) {
                    writeStampAnnot(graphics, stampAnnotEntity, stampAnnot);
                }
            }
        }

        // ??????????????????
        for (AnnotionEntity annotionEntity : reader.getAnnotationEntities()) {
            if (pageId.equals(annotionEntity.getPageId()) && null != annotionEntity.getAnnots()) {
                for (Annot annot : annotionEntity.getAnnots()) {
                    Appearance appearance = annot.getAppearance();
                    writeContent(graphics, appearance, null, null);
                }
            }
        }

    }


    private void writeContent(Graphics2D graphics, CT_PageBlock pageBlock, List<CT_DrawParam> drawParams, Matrix parentMatrix) {
        graphics = (Graphics2D) graphics.create();
        try {
            if (drawParams == null) drawParams = new ArrayList<>();
            if (parentMatrix == null) parentMatrix = MatrixUtils.base();

            if (pageBlock instanceof CT_Layer) {
                ST_RefID drawParamRef = ((CT_Layer) pageBlock).getDrawParam();
                addDrawParams(drawParams, drawParamRef);
            }

            if (pageBlock.attribute("Boundary") != null) {
                String data = (String) pageBlock.attribute("Boundary").getData();
                ST_Box stBox = ST_Box.getInstance(data);
                parentMatrix = MatrixUtils.move(parentMatrix, stBox.getTopLeftX(), stBox.getTopLeftY());
            }

            for (PageBlockType object : pageBlock.getPageBlocks()) {
                try {

                    if (object instanceof CT_GraphicUnit) {
                        drawParams = addDrawParams(drawParams, (CT_GraphicUnit) object);
                    }

                    if (object instanceof TextObject) {
                        TextObject textObject = (TextObject) object;
                        writeText(graphics, textObject, drawParams, parentMatrix);
                    } else if (object instanceof ImageObject) {
                        ImageObject imageObject = (ImageObject) object;
                        writeImage(graphics, imageObject, drawParams, parentMatrix);
                    } else if (object instanceof PathObject) {
                        PathObject pathObject = (PathObject) object;
                        writePath(graphics, pathObject, drawParams, parentMatrix);
                    } else if (object instanceof CompositeObject) {
                        CompositeObject compositeObject = (CompositeObject) object;
                        writeComposite(graphics, compositeObject, drawParams, parentMatrix);
                    } else if (object instanceof CT_PageBlock) {
                        CT_PageBlock block = (CT_PageBlock) object;
                        writeContent(graphics, block, drawParams, parentMatrix);
                    } else if (object instanceof CT_Layer) {
                        CT_Layer layer = (CT_Layer) object;
                        ST_RefID drawParamRef = layer.getDrawParam();
                        if (drawParamRef != null) {
                            CT_DrawParam ctDrawParam = resourceManage.getDrawParam(drawParamRef.getRefId().toString());
                            drawParams.add(ctDrawParam);
                        }
                        writeContent(graphics, layer, drawParams, parentMatrix);
                    }
                } catch (Exception e) {
                    logger.warn("PageBlock????????????:", e);
                }
            }
        } finally {
            graphics.dispose();
        }
    }

    private void writeComposite(Graphics2D graphics, CompositeObject compositeObject, List<CT_DrawParam> drawParams, Matrix parentMatrix) {
        ST_RefID refID = compositeObject.getResourceID();
        if (refID == null) return;

        CT_VectorG vectorG = resourceManage.getCompositeGraphicUnit(refID.getRefId().getId().toString());
        if (vectorG == null) return;
        ST_Box boundary = compositeObject.getBoundary();

        Matrix m = MatrixUtils.base();

        if (compositeObject.getCTM() != null) {
            m = m.mtimes(MatrixUtils.ctm(compositeObject.getCTM().toDouble()));
        }
        if (boundary != null) {
            m = MatrixUtils.move(m, boundary.getTopLeftX(), boundary.getTopLeftY());
        }
        m = m.mtimes(parentMatrix);

        writeContent(graphics, vectorG.getContent(), drawParams, m);
    }

    /*
     * ????????????????????????????????????
     *
     * DrawParam???????????????????????????????????????????????????????????????
     * */
    private List<CT_DrawParam> addDrawParams(List<CT_DrawParam> drawParams, ST_RefID refID) {
        drawParams = new ArrayList<>(drawParams);
        if (refID != null) {
            CT_DrawParam ctDrawParam = resourceManage.getDrawParam(refID.getRefId().toString());
            if (ctDrawParam != null) {
                drawParams.add(ctDrawParam);
            }
        }
        return drawParams;
    }

    private List<CT_DrawParam> addDrawParams(List<CT_DrawParam> drawParams, CT_GraphicUnit graphicUnit) {
        return addDrawParams(drawParams, graphicUnit.getDrawParam());
    }


    private void writePath(Graphics2D graphics, PathObject pathObject, List<CT_DrawParam> drawParams, Matrix parentMatrix) {
        ST_Box boundary = pathObject.getBoundary();
        Matrix baseMatrix = renderBoundaryAndSetClip(graphics, boundary, parentMatrix);
        Matrix matrix = MatrixUtils.base();
        if (pathObject.getCTM() != null) {
            matrix = matrix.mtimes(MatrixUtils.ctm(pathObject.getCTM().toDouble()));
        }

        if (boundary != null) {
            matrix = MatrixUtils.move(matrix, boundary.getTopLeftX(), boundary.getTopLeftY());
        }
        matrix = matrix.mtimes(baseMatrix);
        graphics.transform(MatrixUtils.createAffineTransform(matrix));

        Path2D path2D = buildPath(pathObject.getAbbreviatedData());


        if (pathObject.getStroke() == null || pathObject.getStroke()) {
            graphics.setStroke(new BasicStroke(getLineWidth(pathObject, drawParams).floatValue()));
            Color strokeColor = getStrokeColor(pathObject.getStrokeColor(), CT_Color.rgb(0, 0, 0), drawParams);
            if (strokeColor != null) {
                graphics.setColor(strokeColor);
                graphics.draw(path2D);
            }
        }
        Color tihuan =new Color(0,0,0);

        if (pathObject.getFill() != null && pathObject.getFill()) {
            Color fillColor = getFillColor(pathObject.getFillColor(), null, drawParams);
            if (fillColor != null) {

                if(fillColor.equals(tihuan)){
                }else {
                    graphics.setColor(fillColor);
                    graphics.fill(path2D);
                }
            }
        }
    }

    private void writeImage(Graphics2D graphics, ImageObject imageObject, List<CT_DrawParam> drawParams, Matrix parentMatrix) {
        ST_Box boundary = imageObject.getBoundary();
        Matrix baseMatrix = renderBoundaryAndSetClip(graphics, boundary, parentMatrix);

        BufferedImage image;
        try {
            // ??????????????????????????????
            image = resourceManage.getImage(imageObject);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (image == null) {
            return;
        }
        Matrix m = MatrixUtils.base();
        // ??????????????????1*1
        m = MatrixUtils.scale(m, Double.valueOf(1.0 / image.getWidth()).floatValue(), Double.valueOf(1.0 / image.getHeight()).floatValue());

        if (imageObject.getCTM() != null) {
            m = m.mtimes(MatrixUtils.ctm(imageObject.getCTM().toDouble()));
        }
        if (boundary != null) {
            m = MatrixUtils.move(m, boundary.getTopLeftX(), boundary.getTopLeftY());
        }
        m = m.mtimes(baseMatrix);

        graphics.setTransform(MatrixUtils.createAffineTransform(MatrixUtils.base()));

        float alpha = 1;
        if (imageObject.attributeValue("Alpha") != null) {
            alpha = imageObject.getAlpha() / 255.0f;
        }
        Composite oldComposite = graphics.getComposite();
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, alpha));
        graphics.setTransform(MatrixUtils.createAffineTransform(MatrixUtils.base()));
        graphics.drawImage(image, MatrixUtils.createAffineTransform(m), null);
        graphics.setComposite(oldComposite);

    }

    private void writeText(Graphics2D graphics, TextObject textObject, List<CT_DrawParam> drawParams, Matrix parentMatrix) {
        logger.debug("???????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????TextObject???????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????");
        final Double fontSize = textObject.getSize();
        Color strokeColor = getStrokeColor(textObject.getStrokeColor(), null, drawParams);
        Color fillColor = getFillColor(textObject.getFillColor(), null, drawParams);
        if (fillColor == null) fillColor = Color.black;

        ST_Box boundary = textObject.getBoundary();
        Matrix baseMatrix = renderBoundaryAndSetClip(graphics, boundary, parentMatrix);

        BasicStroke basicStroke = new BasicStroke(getLineWidth(textObject, drawParams).floatValue() * 15, 0, 0);
        graphics.setStroke(basicStroke);

        // ????????????
        FontWrapper<TrueTypeFont> fontWrapper = getFont(textObject);
        TrueTypeFont typeFont = fontWrapper.getFont();
        List<Number> fontMatrix = null;

        if (typeFont == null) {
            logger.info("??????????????????ID???" + textObject.getFont());
            typeFont = FontLoader.getInstance().loadDefaultFont();
        } else {
            try {
                fontMatrix = typeFont.getFontMatrix();
            } catch (Exception e) {
                logger.warn("??????????????????", e);
            }
        }

        // ????????????????????????
        CGTransformMap tsfMap = new CGTransformMap(textObject);
        /*
        ????????????????????????TextObject ??????????????????TextCode???
        ??????TextCode?????????????????????TextCode????????????????????????
        ?????????????????????????????????????????????TextObject????????????????????????
         */
        int globalOffset = 0;
        Double previousX = null;
        Double previousY = null;
        for (TextCode textCode : textObject.getTextCodes()) {
            // ?????????????????????????????????
            String content = StringUtils.removeNewline(textCode.getContent());
            // ???TextCode ??????????????????
            int len = content.length();
            // ????????????????????????????????? ??? ??????????????????????????????
            int offset = 0;
            // ??????????????????????????????
            List<GeneralPath> tbDrawChars = new ArrayList<>(5);
            while (offset < len) {
                CT_CGTransform tsfInfo = tsfMap.get(globalOffset);
                // ????????????????????????????????????cmap????????????
                if (tsfInfo == null) {
                    char c = content.charAt(offset);
                    try {
                        // ??????????????????????????????
                        GlyphData glyphData = typeFont.getUnicodeGlyph(c);
                        tbDrawChars.add(glyphData.getPath());
                    } catch (IOException e) {
                        tbDrawChars.add(null);
                        logger.debug(String.format("??????????????? unicode: %c", c));
                    }
                    globalOffset++;
                    offset++;
                } else {
                    // ??????????????????????????????
                    int codeCount = tsfInfo.getCodeCount();
                    // ???????????????????????????????????????
                    int glyphCount = tsfInfo.getGlyphCount();
                    // ???????????????????????????????????????????????????????????????
                    int[] glyphIndexArr = tsfInfo.getGlyphs().expectIntArr(glyphCount);
                    /*
                     * ??????????????????????????????????????????????????????????????????DeltaX???DeltaY?????????
                     * ????????????????????????
                     *
                     * "?????????"???????????????????????????????????? ????????????????????????????????????????????????????????????
                     * ??????????????????????????????
                     */
                    for (int gid : glyphIndexArr) {

                        try {
                            // ????????????????????????????????????????????????
                            GeneralPath drawPath = typeFont.getPath(gid);
                            tbDrawChars.add(drawPath);
                        } catch (IOException e) {
                            tbDrawChars.add(null);
                            logger.debug(String.format("??????????????? gid: %s", gid));
                        }
                    }
                    // ?????????????????????????????????????????????????????????
                    globalOffset += codeCount;
                    offset += codeCount;
                }
            }
            /*
             * ????????????????????????????????????????????????
             *
             * ????????????????????????????????? ??? ??????????????? ????????????
             */
            List<Double> deltaX = parseDelta(textCode.getDeltaX());
            List<Double> deltaY = parseDelta(textCode.getDeltaY());
            Double x = textCode.getX();
            // ??????X???Y??????????????????????????????TextCode???X???Y???
            if (x == null && previousX != null) {
                x = previousX;
            } else if (x == null) {
                x = 0.0;
            }
            Double y = textCode.getY();
            if (y == null && previousY != null) {
                y = previousY;
            } else if (y == null) {
                y = 0.0;
            }

            for (int drawOffset = 0; drawOffset < tbDrawChars.size(); drawOffset++) {
                // ??????????????????X???Y???????????????X???Y

                // ????????????????????????????????????
                int deltaOffset = drawOffset - 1;
                if (deltaOffset >= 0) {
                    // ?????????????????????????????????????????????
                    if (deltaX.size() > 0) {
                        // ??????X?????????
                        if (deltaOffset < deltaX.size()) {
                            x += deltaX.get(deltaOffset);
                        } else {
                            // ??????deltaX ???????????????????????????????????????deltaX????????????????????????????????????????????????????????????????????????????????????
                            x += deltaX.get(deltaX.size() - 1);
                        }
                    }
                    if (deltaY.size() > 0) {
                        // ??????Y?????????
                        if (deltaOffset < deltaY.size()) {
                            y += deltaY.get(deltaOffset);
                        } else {
                            y += deltaY.get(deltaY.size() - 1);
                        }
                    }
                }
                GeneralPath shape = tbDrawChars.get(drawOffset);
                if (shape == null) {
                    // ?????????????????????????????????
                    continue;
                }
                // ??????????????????????????????
                Matrix matrix = chatMatrix(textObject, x, y, fontSize, fontMatrix, baseMatrix);
                renderChar(graphics, shape, matrix, strokeColor, fillColor);
            }
            // ???????????????TextCode???X???Y??????????????? X???Y?????????
            if (textCode.getX() != null) {
                previousX = textCode.getX();
            }
            if (textCode.getY() != null) {
                previousY = textCode.getY();
            }
        }
        logger.debug("????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????");
    }

    private Matrix chatMatrix(TextObject ctText, Double deltaX, Double deltaY, Double fontSize, List<Number> fontMatrix, Matrix baseMatrix) {
        Matrix m = MatrixUtils.base();
        m = MatrixUtils.imageMatrix(m, 0, 1, 0);
        if (ctText.getHScale() != null) {
            m = MatrixUtils.scale(m, ctText.getHScale(), 1);
        }
        m = m.mtimes(MatrixUtils.create(fontMatrix.get(0).doubleValue(), fontMatrix.get(1).doubleValue(),
                fontMatrix.get(2).doubleValue(), fontMatrix.get(3).doubleValue(),
                fontMatrix.get(4).doubleValue(), fontMatrix.get(5).doubleValue()));
        m = MatrixUtils.scale(m, fontSize, fontSize);
        m = MatrixUtils.move(m, deltaX, deltaY);
        if (ctText.getCTM() != null) {
            m = m.mtimes(MatrixUtils.ctm(ctText.getCTM().toDouble()));
        }
        if (ctText.getBoundary() != null) {
            m = MatrixUtils.move(m, ctText.getBoundary().getTopLeftX(), ctText.getBoundary().getTopLeftY());
        }
        m = m.mtimes(baseMatrix);
        return m;
    }

    private void renderChar(Graphics2D graphics, Shape shape, Matrix m, Color stroke, Color fill) {
        if (shape == null) return;
        graphics.setClip(null);
        graphics.setTransform(MatrixUtils.createAffineTransform(m));
//        graphics.setStroke(new BasicStroke(0.1f));
        graphics.setColor(Color.BLACK);
        graphics.setBackground(Color.white);
        if (stroke != null) {
            graphics.setColor(stroke);
            graphics.draw(shape);
        }
        if (fill != null) {
            graphics.setColor(fill);
            graphics.fill(shape);
        }
    }


    /**
     * ??????????????????????????????
     *
     * @param textObject ????????????
     * @return ??????
     */
    private FontWrapper<TrueTypeFont> getFont(TextObject textObject) {
        ST_RefID stRefID = textObject.getFont();
        if (stRefID == null) return null;

        CT_Font ctFont = resourceManage.getFont(stRefID.toString());
        if (ctFont == null) {
            return null;
        }

        String key = String.format("%s_%s_%s", ctFont.getFamilyName(), ctFont.getFontName(), ctFont.getFontFile());
        if (fontCache.containsKey(key)) {
            // ??????????????????????????????????????????????????????
            return fontCache.get(key);
        }
        // ????????????
        FontWrapper<TrueTypeFont> trueTypeFont = FontLoader.getInstance().loadFontSimilar(this.reader.getResourceLocator(), ctFont);
        // ???????????? ?????? trueTypeFont ??????????????????????????????(null)?????????????????????
        fontCache.put(key, trueTypeFont);
        return trueTypeFont;
    }

    private Matrix renderBoundaryAndSetClip(Graphics2D graphics, ST_Box boundary, Matrix parentMatrix) {
        graphics.setColor(Color.RED);
        graphics.setStroke(new BasicStroke(0.1f * (float) ppm));
        Matrix m = MatrixUtils.base().mtimes(parentMatrix);
        if (boundary != null) {
            /*
             * ??????????????????????????????????????????
             * */
            graphics.setTransform(new AffineTransform());
            Polygon shape = new Polygon();
            Tuple2<Double, Double> p00 = MatrixUtils.pointTransform(m, boundary.getTopLeftX(), boundary.getTopLeftY() - 1);
            Tuple2<Double, Double> p01 = MatrixUtils.pointTransform(m, boundary.getTopLeftX() + boundary.getWidth() + 2, boundary.getTopLeftY() - 1);
            Tuple2<Double, Double> p10 = MatrixUtils.pointTransform(m, boundary.getTopLeftX(), boundary.getTopLeftY() + boundary.getHeight() + 2);
            Tuple2<Double, Double> p11 = MatrixUtils.pointTransform(m, boundary.getTopLeftX() + boundary.getWidth() + 2, boundary.getTopLeftY() + boundary.getHeight() + 2);

            shape.addPoint(Double.valueOf(p00.getFirst() * ppm).intValue(), Double.valueOf(p00.getSecond() * ppm).intValue());
            shape.addPoint(Double.valueOf(p01.getFirst() * ppm).intValue(), Double.valueOf(p01.getSecond() * ppm).intValue());
            shape.addPoint(Double.valueOf(p11.getFirst() * ppm).intValue(), Double.valueOf(p11.getSecond() * ppm).intValue());
            shape.addPoint(Double.valueOf(p10.getFirst() * ppm).intValue(), Double.valueOf(p10.getSecond() * ppm).intValue());

            graphics.setClip(null);
            if (config.drawBoundary) {
                graphics.draw(shape);
            }
            if (config.clip) {
                graphics.setClip(shape);
            }
        }

        m = MatrixUtils.scale(m, ppm, ppm);
        return m;
    }

    private Path2D buildPath(String abbreviatedData) {
        // Path ??????????????????
        LinkedList<OptVal> optValArr = AbbreviatedData.parse(abbreviatedData);
        Path2D path = new Path2D.Double();
        path.moveTo(0, 0);
        for (OptVal optVal : optValArr) {
            double[] arr = optVal.expectValues();
            switch (optVal.opt) {
                case "S":
                case "M":
                    path.moveTo(arr[0], arr[1]);
                    break;
                case "L":
                    path.lineTo(arr[0], arr[1]);
                    break;
                case "Q":
                    path.quadTo(arr[0], arr[1], arr[2], arr[3]);
                    break;
                case "B":
                    path.curveTo(arr[0], arr[1], arr[2], arr[3], arr[4], arr[5]);
                    break;
                case "A":
                    // path.append(new
                    // Arc2D.Double(Double.valueOf(s[i+1]),Double.valueOf(s[i+2]),Double.valueOf(s[i+3]),Double.valueOf(s[i+4]),Double.valueOf(s[i+5]),Double.valueOf(s[i+3]),-1),true);
                    break;
                case "C":
                    path.closePath();
                    break;
            }
        }
        return path;
    }

    private Double getLineWidth(CT_GraphicUnit graphicUnit, List<CT_DrawParam> drawParams) {
        Double lineWidth = graphicUnit.getLineWidth();
        if (lineWidth != null) return lineWidth;
        logger.debug("LineWidth ????????????????????????0.4??????");
        return 0.4;
    }

    private Color getStrokeColor(CT_Color color, CT_Color defaultColor, List<CT_DrawParam> drawParams) {
        CT_Color c = color;
        if (c == null) {
            for (CT_DrawParam drawParam : drawParams) {
                c = drawParam.getStrokeColor();
                if (c != null)
                    break;
            }
        }
        if (c == null) {
            c = defaultColor;
        }
        return getColor(c);
    }

    private Color getFillColor(CT_Color color, CT_Color defaultColor, List<CT_DrawParam> drawParams) {
        CT_Color c = color;
        if (c == null) {
            for (CT_DrawParam drawParam : drawParams) {
                c = drawParam.getFillColor();
                if (c != null)
                    break;
            }
        }
        if (c == null) {
            c = defaultColor;
        }
        return getColor(c);
    }

    public Color getColor(CT_Color ctColor) {
        if (ctColor == null) return null;
        ST_Array array = ctColor.getValue();


        OFDColorSpaceType type = OFDColorSpaceType.RGB;
        ST_RefID refID = ctColor.getColorSpace();
        CT_ColorSpace ctColorSpace = null;
        if (refID != null) {
            ctColorSpace = resourceManage.getColorSpace(refID.toString());
        }
        if (ctColorSpace != null) {
            if (ctColorSpace.getType() != null) {
                type = ctColorSpace.getType();
            }
            if (array == null && ctColor.getIndex() != null) {
                array = ctColorSpace.getPalette().getColorByIndex(ctColor.getIndex());
            }

        }
        if (array == null) return null;
        int[] color = new int[array.size()];
        for (int i = 0; i < array.size(); i++) {
            String s = array.getArray().get(i);
            if (s.startsWith("#")) {
                color[i] = Integer.parseInt(s.replaceAll("#", ""), 16);
            } else {
                if ("0.0".equals(s))
                    color[i] = 0;
                else
                    color[i] = Integer.parseInt(s);
            }
        }
        switch (type) {
            case GRAY:
                return new Color(color[0], color[0], color[0]);
            case CMYK:
                int r = 255 * (100 - color[0]) * (100 - color[3]) / 10000;
                int g = 255 * (100 - color[1]) * (100 - color[3]) / 10000;
                int b = 255 * (100 - color[2]) * (100 - color[3]) / 10000;
                return new Color(r, g, b);
            case RGB:
            default:
                return new Color(color[0], color[1], color[2]);
        }
    }

    public static List<Double> parseDelta(ST_Array array) {
        if (array == null) return new ArrayList<>(0);
        List<Double> arr = new ArrayList<>();

        int i = 0;
        while (i < array.size()) {
            String current = array.getArray().get(i);
            if ("g".equals(current)) {
                int num = Integer.parseInt(array.getArray().get(i + 1));
                Double delta = Double.valueOf(array.getArray().get(i + 2));
                for (int j = 1; j <= num; j++) {
                    arr.add(delta);
                }
                i += 3;
            } else {
                Double delta = Double.valueOf(current);
                arr.add(delta);
                i++;
            }
        }
        return arr;
    }

    public static class Config {
        /*
         * ???????????????
         * */
        private float stampOpacity = 0.75f;
        /*
         * ???????????????????????????
         * */
        private boolean clearStampBackground = true;
        /*
         * ?????????????????????????????????????????????????????????
         * */
        private int stampBackgroundGray = 255;

        /*
         * ???????????????????????? ??????????????????
         * */
        private boolean drawBoundary;

        /*
         * ??????????????????
         * */
        private boolean clip = true;

        public float getStampOpacity() {
            return stampOpacity;
        }

        public void setStampOpacity(float stampOpacity) {
            if (stampOpacity >= 0 && stampOpacity <= 1) {
                this.stampOpacity = stampOpacity;
            }
        }

        public boolean isDrawBoundary() {
            return drawBoundary;
        }

        public void setDrawBoundary(boolean drawBoundary) {
            this.drawBoundary = drawBoundary;
        }

        public boolean isClearStampBackground() {
            return clearStampBackground;
        }

        public void setClearStampBackground(boolean clearStampBackground) {
            this.clearStampBackground = clearStampBackground;
        }

        public int getStampBackgroundGray() {
            return stampBackgroundGray;
        }

        /**
         * ?????????????????????????????????????????????????????????
         *
         * @param stampBackgroundGray ????????????
         */
        public void setStampBackgroundGray(int stampBackgroundGray) {
            if (stampBackgroundGray < 0 || stampBackgroundGray > 255) {
                stampBackgroundGray = 255;
            }
            this.stampBackgroundGray = stampBackgroundGray;
        }

        public boolean isClip() {
            return clip;
        }

        public void setClip(boolean clip) {
            this.clip = clip;
        }
    }
}
