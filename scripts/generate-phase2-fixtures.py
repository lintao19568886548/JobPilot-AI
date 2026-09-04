from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parent
FIXTURES = ROOT / "fixtures"
FIXTURES.mkdir(exist_ok=True)

csv_text = """title,companyName,city,salaryText,description,platform,platformJobId
CSV Java Engineer,DEMO CSV Company,Shanghai,18-28K·13薪,必须熟悉 Java 和 Spring Boot；Redis 优先,DEMO_CSV,csv-valid-1
,DEMO CSV Company,Shanghai,15-20K,缺少岗位名称,DEMO_CSV,csv-invalid-1
CSV AI Engineer,DEMO AI Company,Hangzhou,25-35K·14薪,要求熟悉 Python 和 LangGraph；RAG 经验优先,DEMO_CSV,csv-valid-2
"""
(FIXTURES / "phase2-jobs.csv").write_text(csv_text, encoding="utf-8")

rows = [
    ["title", "companyName", "city", "salaryText", "description", "platform", "platformJobId"],
    ["XLSX Platform Engineer", "DEMO Sheet Company", "Shenzhen", "22-32K·14薪", "必须熟悉 Docker 和 Kubernetes；Kafka 优先", "DEMO_XLSX", "xlsx-valid-1"],
]

shared = []
index = {}
for row in rows:
    for value in row:
        if value not in index:
            index[value] = len(shared)
            shared.append(value)

sheet_rows = []
for row_number, row in enumerate(rows, start=1):
    cells = []
    for column_number, value in enumerate(row, start=1):
        column = ""
        number = column_number
        while number:
            number, remainder = divmod(number - 1, 26)
            column = chr(65 + remainder) + column
        cells.append(f'<c r="{column}{row_number}" t="s"><v>{index[value]}</v></c>')
    sheet_rows.append(f'<row r="{row_number}">{"".join(cells)}</row>')

parts = {
    "[Content_Types].xml": """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/></Types>""",
    "_rels/.rels": """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
    "xl/workbook.xml": """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="jobs" sheetId="1" r:id="rId1"/></sheets></workbook>""",
    "xl/_rels/workbook.xml.rels": """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/></Relationships>""",
    "xl/sharedStrings.xml": f'''<?xml version="1.0" encoding="UTF-8"?><sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="{sum(map(len, rows))}" uniqueCount="{len(shared)}">{"".join(f"<si><t>{escape(value)}</t></si>" for value in shared)}</sst>''',
    "xl/worksheets/sheet1.xml": f'''<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>{"".join(sheet_rows)}</sheetData></worksheet>''',
}

with ZipFile(FIXTURES / "phase2-jobs.xlsx", "w", ZIP_DEFLATED) as archive:
    for name, content in parts.items():
        archive.writestr(name, content)

print(f"Generated fixtures in {FIXTURES}")
