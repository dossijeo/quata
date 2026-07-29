# DOC-READER-BOUNDARY-001

El renderer Office/RTF sigue siendo código Android vendorizado y no forma parte del núcleo multiplataforma. El único punto de entrada de la aplicación es `AndroidDocumentOpenService` en `:core`, que admite solo `content://` o HTTPS y convierte archivos propios a `FileProvider`; el bridge del módulo lector nunca recibe ni genera `file://`.

Compatibilidad preservada: PDF, RTF, DOC/DOCX, XLS/XLSX y PPT/PPTX conservan sus MIME explícitos y el host existente abre la actividad legacy. Si el renderer rechaza un documento devuelve `Unsupported`; si falla devuelve un error estable. La sustitución por un renderer KMP queda fuera de alcance hasta que exista paridad de formato y rendimiento en Android, Web e iOS.
