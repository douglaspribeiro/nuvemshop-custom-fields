package br.com.nuvemcustomfields.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.springframework.stereotype.Component;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
public class BuyerFileValidator {
    private final java.util.concurrent.Semaphore processing=new java.util.concurrent.Semaphore(1);
    public record Validated(String name, String type, String format) {}
    public Validated validate(byte[] bytes, String name, Set<String> allowed) {
        if(!processing.tryAcquire()) throw new BuyerUploadLedger.Rejected("upload.rate.limit");
        try { return validateContent(bytes,name,allowed); }
        finally { processing.release(); }
    }
    private Validated validateContent(byte[] bytes, String name, Set<String> allowed) {
        if (bytes.length==0 || bytes.length>BuyerUploadPolicy.MAX_FILE_BYTES) throw new BuyerUploadLedger.Rejected("upload.size.invalid");
        String safe=(name==null ? "arquivo" : name).replace('\\','/');
        safe=safe.substring(safe.lastIndexOf('/')+1).replaceAll("[^\\p{L}\\p{N}._ ()-]","_");
        if (safe.isBlank()) safe="arquivo";
        if (safe.length()>180) safe=safe.substring(safe.length()-180);
        try {
            String format;
            if (bytes.length>8 && new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-")) {
                if (!new String(bytes,Math.max(0,bytes.length-1024),Math.min(1024,bytes.length),StandardCharsets.ISO_8859_1).contains("%%EOF"))
                    throw new IllegalArgumentException();
                try (var pdf=Loader.loadPDF(bytes)) {
                    if (pdf.isEncrypted() || pdf.getNumberOfPages()<1 || pdf.getNumberOfPages()>500) throw new IllegalArgumentException();
                    // Do not release embedded executables, scripts, actions or attachments.
                    var pending=new java.util.ArrayDeque<org.apache.pdfbox.cos.COSBase>();
                    var seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<org.apache.pdfbox.cos.COSBase,Boolean>());
                    pending.add(pdf.getDocumentCatalog().getCOSObject());
                    while(!pending.isEmpty()) {
                        var object=pending.removeFirst();
                        if(!seen.add(object)) continue;
                        if(seen.size()>100_000) throw new IllegalArgumentException();
                        if(object instanceof org.apache.pdfbox.cos.COSObject indirect) {
                            if(indirect.getObject()!=null) pending.add(indirect.getObject());
                        } else if(object instanceof org.apache.pdfbox.cos.COSArray array) {
                            for(var value:array) if(value!=null) pending.add(value);
                        } else if (object instanceof COSDictionary dictionary) {
                            for (String key: new String[]{"JS","JavaScript","OpenAction","AA","EmbeddedFiles","EF","Launch","RichMedia","XFA"})
                                if (dictionary.containsKey(COSName.getPDFName(key))) throw new IllegalArgumentException();
                            String action=dictionary.getNameAsString(COSName.S);
                            if (Set.of("JavaScript","Launch","SubmitForm","ImportData","GoToR").contains(action==null?"":action)) throw new IllegalArgumentException();
                            for(var value:dictionary.getValues()) if(value!=null) pending.add(value);
                        }
                    }
                    for (var page:pdf.getPages()) if (!page.getAnnotations().isEmpty()) throw new IllegalArgumentException();
                }
                format="PDF";
            } else {
                try (var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                    var readers=ImageIO.getImageReaders(stream);
                    if (!readers.hasNext()) throw new IllegalArgumentException();
                    var reader=readers.next();
                    try {
                        reader.setInput(stream,true,true);
                        format=reader.getFormatName().toUpperCase(java.util.Locale.ROOT);
                        if (format.equals("JPG")) format="JPEG";
                        if (!Set.of("JPEG","PNG").contains(format)) throw new IllegalArgumentException();
                        int width=reader.getWidth(0),height=reader.getHeight(0);
                        if (width<=0 || height<=0 || (long)width*height>40_000_000) throw new IllegalArgumentException();
                        var image=reader.read(0);
                        if(image==null) throw new IllegalArgumentException();
                        image.flush();
                    } finally {reader.dispose();}
                }
                // Reject missing terminal markers rather than accepting a partial decode.
                if (format.equals("JPEG") && (bytes[bytes.length-2]!=(byte)0xff || bytes[bytes.length-1]!=(byte)0xd9)) throw new IllegalArgumentException();
                if (format.equals("PNG") && (bytes.length<12 || !new String(bytes,bytes.length-8,4,StandardCharsets.US_ASCII).equals("IEND"))) throw new IllegalArgumentException();
            }
            if (!allowed.contains(format)) throw new IllegalArgumentException();
            return new Validated(safe,format.equals("PDF")?"application/pdf":format.equals("PNG")?"image/png":"image/jpeg",format);
        } catch(Exception e) { throw new BuyerUploadLedger.Rejected("upload.content.invalid"); }
    }
}
