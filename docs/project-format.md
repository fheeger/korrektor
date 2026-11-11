# Project format

Specification of the korrektor project format, **version 1**. The format is text-based, easy to diff, and stable: projects are exchanged between people and versions of the app. The reasons for the choices are in [decisions.md](decisions.md), D03. The code is in the package `korrektor.format` (see [architecture.md](architecture.md)).

The examples in this document are checked by a test (`SpecExamplesTest`): each one is read, written again, and must come out identical.

## Folder layout

A project is a folder named `<title>.korrektur`:

```
<title>.korrektur/
├── project.json          format marker, version, page list
├── source.pdf            copy of the source PDF, never modified
├── pages/
│   ├── 0001.ocr.json     original OCR layer of page 1, written once, never changed
│   ├── 0001.json         corrected layer of page 1
│   └── …
├── history/
│   └── 0001.jsonl        undo history of page 1, append-only
└── .lock                 present while the project is open (S07), not content
```

- **Page numbers.** The page number `n` of a book page is its position in the `pages` list of `project.json`, starting at 1. Page files are named with `n` padded to at least four digits (`0001`, `0042`, `12345`).
- **PDF pages.** Each page entry says which page of `source.pdf` it shows (`pdfPage`, 1-based). Today these are the same numbers in the same order, but the format does not rely on it.
- **Not content.** `.lock` and files ending in `.tmp` are working files. `.tmp` files are leftovers of an interrupted write; they are deleted when a project is opened and must be ignored when sharing or comparing projects.
- Files and folders the reader does not know are left alone.

## Version and compatibility

- `project.json` holds `"format": "korrektur-project"` and an integer `"version"`. The version is 1.
- **Readers ignore unknown fields and keep them.** A field a reader does not know is read, stays attached to the object it was found in, and is written back unchanged. So a project opened and saved by an older app loses nothing.
- **New fields do not change the version.** Fields (such as the metadata of S02 or a page status of S09), region types and new files can be added at any time.
- **The version is raised only for incompatible changes**, for example a changed meaning of an existing field. A reader that finds a higher version than it supports must refuse to open the project, say so, and change nothing. It must not guess.
- A project whose `format` is missing or different is not a korrektur project and is refused.

## Writing rules

Every JSON file is written canonically, so that opening and saving a project without changes leaves every file **byte-for-byte identical**.

- UTF-8, no byte order mark, LF line endings, one trailing newline at the end of each file.
- Separators are `, ` and `: `. Where this document shows a layout (one object per line, indentation), writers follow it exactly.
- **One text line of the book is one line of the file.** Correcting a word changes one line in a diff.
- **Key order.** The known fields of each object are written in the order shown in the tables below. Unknown fields follow, in alphabetical order. Objects inside unknown fields have their keys in alphabetical order.
- **Optional fields** that are empty (`box`, `origin`) are left out, not written as `null`. Readers also accept `null`.
- **Coordinates** (the values of boxes, and the page `width` and `height`) are written with exactly one decimal (`12.0`, not `12`), rounded half up. `-0.0` is written as `0.0`. Readers accept any JSON number and round it the same way. All other numbers (`pdfPage`, `order`, and numbers in unknown fields) are written as integers or as they were read.
- **Strings** are written with only the escapes JSON requires: `\"`, `\\`, `\n`, `\r`, `\t`, `\b`, `\f`, and `\u00xx` for other control characters. Everything else is written as it is, including non-ASCII characters and `/`.
- **No Unicode normalization.** Text is stored exactly as typed (V1): `ſ`, a combining `ͤ` after `u`, `ꝛ`, and precomposed and decomposed forms stay as they are. Text that cannot be encoded in UTF-8 (a lone surrogate) is refused.
- Every file is replaced crash-safely: the new content is written to `<name>.tmp`, flushed to disk, and renamed over the old file. After a crash a file has either its old or its new content. The history file is the one exception (see below).

## Coordinates

- Unit: PDF points (1/72 inch). Origin: top-left corner of the page, x to the right, y downwards.
- A **box** is `[x, y, width, height]`.
- `width` and `height` of a page are the size of the PDF page in points.

## `project.json`

```json project
{
  "format": "korrektur-project",
  "version": 1,
  "pages": [
    {"pdfPage": 1},
    {"pdfPage": 2},
    {"pdfPage": 3}
  ]
}
```

| Field | Type | Meaning |
|---|---|---|
| `format` | string | always `"korrektur-project"` |
| `version` | integer | format version, 1 |
| `pages` | array | one entry per book page, in page order; one entry per line |
| `pages[].pdfPage` | integer ≥ 1 | the page of `source.pdf` |

Further fields (metadata, page status) are added by later stories as new fields. Unknown fields are kept, at the top level and in page entries. An empty book is written as `"pages": []`.

## Page files

`pages/NNNN.ocr.json` holds the **original OCR layer** of a page. It is written once, when the page is imported, and never changed. `pages/NNNN.json` holds the **corrected layer**: a full copy of the text that starts as a copy of the original and is edited. Both files have the same structure.

A corrected layer keeps the IDs of the regions and lines it started from, and each corrected line names the original line it came from in `origin`. A line the user added has a new ID and no `origin`. A line without a `box` (and words without boxes) is allowed in the corrected layer: it has no place on the scan.

Original layer of page 3:

```json page-original
{
  "pdfPage": 3,
  "width": 307.4,
  "height": 488.2,
  "regions": [
    {"id": "r1", "type": "text", "order": 1, "box": [40.0, 40.0, 227.4, 380.0], "lines": [
      {"id": "l1", "box": [40.0, 52.0, 227.4, 10.2], "words": [["Von", 40.0, 52.0, 20.0, 10.2], ["der", 64.0, 52.0, 18.0, 10.2], ["Kriegsfüh-", 86.0, 52.0, 50.5, 10.2]]},
      {"id": "l2", "box": [40.0, 64.0, 227.4, 10.2], "words": [["rnng", 40.0, 64.0, 26.0, 10.2], ["iſt", 70.0, 64.0, 12.0, 10.2], ["nuͤtzlich", 86.0, 64.0, 42.0, 10.2]]}
    ]}
  ]
}
```

Corrected layer of the same page: `rnng` is corrected to `rung`, and a line was added.

```json page-corrected
{
  "pdfPage": 3,
  "width": 307.4,
  "height": 488.2,
  "regions": [
    {"id": "r1", "type": "text", "order": 1, "box": [40.0, 40.0, 227.4, 380.0], "lines": [
      {"id": "l1", "box": [40.0, 52.0, 227.4, 10.2], "origin": "l1", "words": [["Von", 40.0, 52.0, 20.0, 10.2], ["der", 64.0, 52.0, 18.0, 10.2], ["Kriegsfüh-", 86.0, 52.0, 50.5, 10.2]]},
      {"id": "l2", "box": [40.0, 64.0, 227.4, 10.2], "origin": "l2", "words": [["rung", 40.0, 64.0, 26.0, 10.2], ["iſt", 70.0, 64.0, 12.0, 10.2], ["nuͤtzlich", 86.0, 64.0, 42.0, 10.2]]},
      {"id": "l3", "words": [["Anmerkung"]]}
    ]}
  ]
}
```

### Page

| Field | Type | Meaning |
|---|---|---|
| `pdfPage` | integer ≥ 1 | the page of `source.pdf`; must equal the `pdfPage` of the page's entry in `project.json` |
| `width`, `height` | number > 0 | page size in points |
| `regions` | array | the regions; written one region per block. An empty page is `"regions": []` |

### Region

A region is a block of the page. It is written as one object line, followed by its lines.

| Field | Type | Meaning |
|---|---|---|
| `id` | string | unique among the regions of the layer; kept when editing |
| `type` | string | `"text"` today. The set is open: other values (e.g. `"marginalia"`) are kept as they are, and later features add types |
| `order` | integer | reading order of the regions on the page; unique within the page |
| `box` | box, optional | the area of the region |
| `lines` | array | the lines in reading order, one per line of the file. `"lines": []` when empty |

### Line

| Field | Type | Meaning |
|---|---|---|
| `id` | string | unique among all lines of the layer; kept when editing |
| `box` | box, optional | the area of the line on the scan; missing for lines without a place on the scan |
| `origin` | string, optional | in the corrected layer: the `id` of the original line this one came from. Missing for new lines |
| `words` | array | the words, in order |

The **text of a line** is its words joined by one space. There is no separate text field, so a line without boxes looks like one with boxes.

### Word

A word is written as an array: `["text", x, y, width, height]`, or `["text"]` if it has no box. A word that carries unknown fields is written as an object instead, with `"t"` (text), `"b"` (box, optional) and the unknown fields:

```json
{"t": "ſo", "b": [1.0, 2.0, 3.0, 4.0], "conf": 0.93}
```

Readers accept both forms for every word.

### Unknown fields

Any object (page, region, line, word object) can carry unknown fields. They are written after the known ones, in alphabetical order, on the same line as the known fields of that object:

```json page-unknown
{
  "pdfPage": 1,
  "width": 100.0,
  "height": 200.0,
  "regions": [
    {"id": "r1", "type": "text", "order": 1, "lines": [
      {"id": "l1", "words": [{"t": "ſo", "b": [1.0, 2.0, 3.0, 4.0], "conf": 0.93}, ["ꝛc."]], "tag": {"a": [1, 2], "b": null}}
    ], "style": "bold"}
  ],
  "scanner": "x"
}
```

## History files

`history/NNNN.jsonl` holds the undo history of one page as [JSON Lines](https://jsonlines.org/): one JSON object per line, written compactly with alphabetical keys, each line ending in LF. What an entry contains is defined by the undo feature (S08); this format only fixes the framing:

```json history
{"line": "l2", "new": "rung", "old": "rnng", "seq": 1, "type": "replace-word"}
{"line": "l3", "seq": 2, "type": "add-line"}
```

(The entries above only illustrate the framing.)

- The file is **append-only**. Existing lines are never rewritten or removed; an undo adds an entry.
- A line counts only once its newline is written. A **torn last line** (without newline, after a crash) is ignored by readers and cut off before the next entry is added.
- Readers find the latest entries by reading from the end of the file; they do not need to read all of it.
- An invalid line anywhere else is an error that names the line. It is never skipped silently.

## Guarantees

- `source.pdf` is never modified after the project is created. It is marked read-only.
- The original OCR layer of a page is written once and never changed.
- Opening and saving without changes leaves every file byte-for-byte identical.
- Reading one page reads only that page's files, not the whole book.
- An interrupted save leaves each file either old or new, never broken. The history can lose at most its last, unfinished line.
- Unknown fields survive opening and saving.

## Extending the format

- **Add a field:** add it to the table above, with its type and when it may be missing. Readers that don't know it keep it. No version change.
- **Add a region type:** name it in the table of region types. No version change.
- **Add a file or folder:** document it in the layout above. No version change if older readers can ignore it without breaking the project.
- **Change the meaning of an existing field, remove a field, or change the layout of a file:** raise `version`, and describe how to read older versions.
- Every ticket that changes the format updates this document in the same change.
