# Third-Party Notices

## flag-icons (country flag vector graphics)

- Source: https://github.com/lipis/flag-icons
- Pinned version: v7.6.0 (https://github.com/lipis/flag-icons/releases/tag/v7.6.0)
- Files used: `flags/1x1/*.svg`, batch-converted to Android vector drawables at
  `V2rayNG/app/src/main/res/drawable/shroxy_flag_<iso>.xml` (512×512 viewport preserved,
  display size 28dp). One source remains excluded because the converter cannot reproduce
  its `<marker>`-based star field (`um`); the US flag (`us`) is instead taken from
  Twemoji (see below).
- License: MIT

```
MIT License

Copyright (c) 2018 Panayiotis Lipiridis

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Twemoji (US flag vector graphic)

- Source: https://github.com/jdecked/twemoji (maintained fork of twitter/twemoji)
- File used: `assets/svg/1f1fa-1f1f8.svg`, converted to the Android vector drawable
  `V2rayNG/app/src/main/res/drawable/shroxy_flag_us.xml` (36×36 viewport preserved,
  display size 28dp).
- License: CC-BY 4.0 (https://creativecommons.org/licenses/by/4.0/)
