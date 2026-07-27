"""Generates tiny, own test-only document fixtures; no third-party content."""
from pathlib import Path
from zipfile import ZipFile, ZIP_STORED

out = Path(__file__).parents[1] / 'core/src/commonTest/resources/documents'
out.mkdir(parents=True, exist_ok=True)
(out / 'fixture.pdf').write_bytes(b'%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Count 0/Kids[]>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n')
(out / 'fixture.rtf').write_text(r'{\rtf1\ansi Quata fixture}', encoding='ascii')
parts = {
 'fixture.docx': 'word/document.xml', 'fixture.pptx': 'ppt/presentation.xml', 'fixture.xlsx': 'xl/workbook.xml'
}
for name, part in parts.items():
    with ZipFile(out / name, 'w', ZIP_STORED) as z:
        z.writestr('[Content_Types].xml', '<Types/>')
        z.writestr('_rels/.rels', '<Relationships/>')
        z.writestr(part, '<root/>')
