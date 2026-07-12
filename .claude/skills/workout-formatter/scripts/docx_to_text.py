#!/usr/bin/env python3
"""Extract plain text from a .docx file, one line per paragraph (tabs preserved).

Usage: docx_to_text.py file.docx [> file.txt]
"""
import re
import sys
import zipfile


def docx_to_text(path):
    with zipfile.ZipFile(path) as z:
        xml = z.read("word/document.xml").decode("utf-8")
    xml = re.sub(r"<w:tab[^>]*/>", "\t", xml)
    lines = []
    for para in re.findall(r"<w:p[ >].*?</w:p>", xml, re.S):
        texts = re.findall(r"<w:t[^>]*>(.*?)</w:t>", para, re.S)
        line = "".join(texts)
        for entity, char in (("&amp;", "&"), ("&lt;", "<"), ("&gt;", ">"), ("&quot;", '"')):
            line = line.replace(entity, char)
        lines.append(line)
    return "\n".join(lines)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    print(docx_to_text(sys.argv[1]))
