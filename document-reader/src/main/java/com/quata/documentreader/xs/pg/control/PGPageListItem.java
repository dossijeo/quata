/*
 * 文件名称:          WPPageListItem.java
 *  
 * 编译器:            android2.2
 * 时间:              上午10:24:57
 */
package com.quata.documentreader.xs.pg.control;

import com.quata.documentreader.xs.pg.model.PGModel;
import com.quata.documentreader.xs.pg.model.PGSlide;
import com.quata.documentreader.xs.pg.view.SlideDrawKit;
import com.quata.documentreader.xs.system.IControl;
import com.quata.documentreader.xs.system.beans.pagelist.APageListItem;
import com.quata.documentreader.xs.system.beans.pagelist.APageListView;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.widget.ProgressBar;

/**
 * word engine "PageListView" component item
 * <p>
 * <p>
 * Read版本:        Read V1.0
 * <p>
 * 作者:            ljj8494
 * <p>
 * 日期:            2013-1-8
 * <p>
 * 负责人:          ljj8494
 * <p>
 * 负责小组:         
 * <p>
 * <p>
 */
public class PGPageListItem extends APageListItem
{
    //
    public static final int BUSY_SIZE = 60;
    //
    private static final int BACKGROUND_COLOR = 0xFFFFFFFF;
    /**
     * 
     * @param content
     * @param parentSize
     */
    public PGPageListItem(APageListView listView, IControl control, PGEditor editor, int pageWidth, int pageHeight)
    {
        super(listView, pageWidth, pageHeight);
        this.control = control;
        this.pgModel= (PGModel)listView.getModel();
        this.editor = editor;
        this.setBackgroundColor(BACKGROUND_COLOR);
    }
    
    /**
     * 
     */
    public void onDraw(Canvas canvas)
    {
        PGSlide slide = pgModel.getSlide(pageIndex);
        if (slide != null)
        {
            float zoom = listView.getZoom();
            SlideDrawKit.instance().drawSlide(canvas, pgModel, editor, slide, zoom);
        }
    }
    
    /**
     * 
     * @param pageIndex     page index (base 0)
     * @param pageWidth     page width of after scaled
     * @param pageHeight    page height of after scaled
     */
    public void setPageItemRawData(final int pIndex, int pageWidth, int pageHeight)
    {
        super.setPageItemRawData(pIndex, pageWidth, pageHeight);
        if (pageIndex >= pgModel.getRealSlideCount())
        {
            showBusyIndicator();
            Thread waitForSlideThread = new Thread(() -> {
                    while (pgModel != null && pageIndex >= pgModel.getRealSlideCount())
                    {
                        try
                        {
                            Thread.sleep(200);
                        }
                        catch (Exception e)
                        {
                            break;
                        }
                    }
                    post(() -> {
                    if (mBusyIndicator != null)
                    {
                        mBusyIndicator.setVisibility(INVISIBLE);
                    }
                    postInvalidate();
                    if (listView != null)
                    {
                        //final APageListItem own = this;
                        /*if ((int)(listView.getZoom() * 100) == 100
                            || (isInit && pIndex == 0))*/
                        if (pageIndex == listView.getCurrentPageNumber() - 1)
                        {
                            listView.exportImage(listView.getCurrentPageView(), null);
                        }
                        isInit = false;
                    }
                    });
            }, "QuataPgPageWait");
            waitForSlideThread.start();
        }
        else
        {
            //final APageListItem own = this;
            if ((int)(listView.getZoom() * 100) == 100
                || (isInit && pIndex == 0))
            {
                listView.exportImage(this, null);
            }
            isInit = false;
            if (mBusyIndicator != null)
            {
                mBusyIndicator.setVisibility(INVISIBLE);
            }
        }
    }
    
    /**
     * 
     */
    public void releaseResources()
    {
        super.releaseResources();
        SlideDrawKit.instance().disposeOldSlideView(pgModel, pgModel.getSlide(pageIndex));
    }

    private void showBusyIndicator()
    {
        if (mBusyIndicator == null)
        {
            mBusyIndicator = new ProgressBar(getContext());
            mBusyIndicator.setIndeterminate(true);
            mBusyIndicator.setBackgroundResource(android.R.drawable.progress_horizontal);
            addView(mBusyIndicator);
        }
        mBusyIndicator.setVisibility(VISIBLE);
    }
    
    /**
     * black page
     * 
     * @param pageIndex page index (base 0)
     */
    public void blank(int pIndex)
    {
        super.blank(pIndex);
    }
    
    /**
     * added reapint image view
     */
    protected void addRepaintImageView(Bitmap bmp)
    {
        postInvalidate();
        listView.exportImage(this, bmp);
    }

    /**
     * remove reapint image view
     */
    protected void removeRepaintImageView()
    {
        
    }
    
    /**
     * 
     *(non-Javadoc)
     * @see APageListItem#onLayout(boolean, int, int, int, int)
     *
     */
    protected void onLayout(boolean changed, int left, int top, int right, int bottom)
    {
        super.onLayout(changed, left, top, right, bottom);
        int w = right - left;
        int h = bottom - top;
        if (mBusyIndicator != null)
        {
            int x, y;
            if (w > listView.getWidth())
            {
                x = (listView.getWidth() - BUSY_SIZE) / 2 - left;
            }
            else
            {
                x = (w - BUSY_SIZE) / 2;
            }
            if (h > listView.getHeight())
            {
                y = (listView.getHeight() - BUSY_SIZE) / 2 - top;
            }
            else
            {
                y = (h - BUSY_SIZE) / 2;
            }
            mBusyIndicator.layout(x, y, x + BUSY_SIZE, y + BUSY_SIZE);
        }
    }

    /**
     * 
     */
    public void dispose()
    {
        super.dispose();
        control = null;
        pgModel = null;
    }
    
    //
    private ProgressBar mBusyIndicator;
    //
    private PGModel pgModel;
    //
    private PGEditor editor;
       
}
