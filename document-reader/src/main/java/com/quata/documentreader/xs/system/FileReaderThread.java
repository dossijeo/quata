/*
 * 文件名称:          FileReaderThread.java
 *  
 * 编译器:            android2.2
 * 时间:              下午8:11:02
 */
package   com.quata.documentreader.xs.system;

import   com.quata.documentreader.xs.constant.MainConstant;
import   com.quata.documentreader.xs.fc.doc.DOCReader;
import   com.quata.documentreader.xs.fc.doc.DOCXReader;
import   com.quata.documentreader.xs.fc.doc.TXTReader;
import   com.quata.documentreader.xs.fc.pdf.PDFReader;
import   com.quata.documentreader.xs.fc.ppt.PPTReader;
import   com.quata.documentreader.xs.fc.ppt.PPTXReader;
import   com.quata.documentreader.xs.fc.xls.XLSReader;
import   com.quata.documentreader.xs.fc.xls.XLSXReader;

import android.os.Handler;
import android.os.Message;


/**
 * 读取文档线程
 * <p>
 * <p>
 * Read版本:        Read V1.0
 * <p>
 * 作者:            ljj8494
 * <p>
 * 日期:            2012-3-12
 * <p>
 * 负责人:          ljj8494
 * <p>
 * 负责小组:         
 * <p>
 * <p>
 */
public class FileReaderThread extends Thread
{
   
    /**
     * 
     * @param activity
     * @param handler
     * @param filePath
     * @param encoding
     */
    public FileReaderThread(IControl control, Handler handler, String filePath, String encoding)
    {
        this.control = control;
        this.handler = handler;
        this.filePath = filePath;
        this.encoding = encoding;
    }
    
    /**
     * 
     *
     */
    public void run()
    {
        // show progress
        Message msg = new Message();
        msg.what = MainConstant.HANDLER_MESSAGE_SHOW_PROGRESS;
        handler.handleMessage(msg);
        
        msg = new Message();
        msg.what = MainConstant.HANDLER_MESSAGE_DISMISS_PROGRESS;
        try
        {
            IReader reader = null;
            String fileName = filePath.toLowerCase();
            // doc
            if (fileName.endsWith(MainConstant.FILE_TYPE_DOC))
            {
                reader = new DOCReader(control, filePath);
            }
            // docx
            else if (fileName.endsWith(MainConstant.FILE_TYPE_DOCX))
            {
                reader = new DOCXReader(control, filePath);;
            }
            //
            else if (fileName.endsWith(MainConstant.FILE_TYPE_TXT))
            {
                reader = new TXTReader(control, filePath, encoding);
            }
            // xls
            else if (fileName.endsWith(MainConstant.FILE_TYPE_XLS))
            {
                reader = new XLSReader(control, filePath);
            }
            // xlsx
            else if (fileName.endsWith(MainConstant.FILE_TYPE_XLSX))
            {
                reader = new XLSXReader(control, filePath);
            }
            // ppt
            else if (fileName.endsWith(MainConstant.FILE_TYPE_PPT))
            {
                reader = new PPTReader(control, filePath);
            }
            // pptx
            else if (fileName.endsWith(MainConstant.FILE_TYPE_PPTX))
            {
                reader = new PPTXReader(control, filePath);
            }
            // PDF document
            else if (fileName.endsWith(MainConstant.FILE_TYPE_PDF))
            {
                reader = new PDFReader(control, filePath);
            }
            // other
            else
            {
                reader = new TXTReader(control, filePath, encoding);
            }
            // 把IReader实例传出
            Message mesReader = new Message();
            mesReader.obj = reader;
            mesReader.what = MainConstant.HANDLER_MESSAGE_SEND_READER_INSTANCE;
            handler.handleMessage(mesReader);
            
            
            msg.obj = reader.getModel();
            reader.dispose();
            msg.what = MainConstant.HANDLER_MESSAGE_SUCCESS;            
            //handler.handleMessage(msg);
        }
        catch (OutOfMemoryError eee)
        {
            msg.what = MainConstant.HANDLER_MESSAGE_ERROR;
            msg.obj = eee;
        }
        catch (Exception ee)
        {
            msg.what = MainConstant.HANDLER_MESSAGE_ERROR;
            msg.obj = ee;
        }
        catch (AbortReaderError ee)
        {
            msg.what = MainConstant.HANDLER_MESSAGE_ERROR;
            msg.obj = ee;
        }
        finally
        {   
            handler.handleMessage(msg);
            control = null;
            handler= null;
            encoding = null;
            filePath = null;
        }
    }
    
    //
    private String encoding;
    //
    private String filePath;
    //
    private Handler handler;
    //
    private IControl control;
}
