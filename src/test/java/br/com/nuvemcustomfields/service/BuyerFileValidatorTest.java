package br.com.nuvemcustomfields.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class BuyerFileValidatorTest {
    private final BuyerFileValidator validator=new BuyerFileValidator();
    private byte[] pdf(boolean script) throws Exception {
        try(var document=new PDDocument();var output=new ByteArrayOutputStream()) {
            document.addPage(new PDPage());if(script)document.getDocumentCatalog().setOpenAction(new PDActionJavaScript("app.alert('test')"));
            document.save(output);return output.toByteArray();
        }
    }
    @Test void acceptsActualPdfAndRejectsScriptAndDisallowedFormat() throws Exception {
        assertEquals("application/pdf",validator.validate(pdf(false),"arte.pdf",Set.of("PDF")).type());
        assertThrows(BuyerUploadLedger.Rejected.class,()->validator.validate(pdf(true),"arte.pdf",Set.of("PDF")));
        assertThrows(BuyerUploadLedger.Rejected.class,()->validator.validate(pdf(false),"arte.pdf",Set.of("PNG")));
    }
    @Test void rejectsDisguisedAndTruncatedPdf() throws Exception {
        assertThrows(BuyerUploadLedger.Rejected.class,()->validator.validate("<script>bad()</script>".getBytes(),"arte.pdf",Set.of("PDF")));
        byte[] complete=pdf(false);
        assertThrows(BuyerUploadLedger.Rejected.class,()->validator.validate(java.util.Arrays.copyOf(complete,complete.length/2),"arte.pdf",Set.of("PDF")));
    }
}
