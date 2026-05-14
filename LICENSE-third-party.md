# Third-party attributions

## react-native-body-highlighter

The SVG path data for the front and back body silhouettes and individual muscle
regions in [`app/src/main/java/com/example/gymtime/ui/home/BodyAnatomy.kt`](app/src/main/java/com/example/gymtime/ui/home/BodyAnatomy.kt)
is ported from [HichamELBSI/react-native-body-highlighter](https://github.com/HichamELBSI/react-native-body-highlighter),
specifically the `assets/bodyFront.ts`, `assets/bodyBack.ts`, and
`components/SvgMaleWrapper.tsx` files. Only the polygon coordinate data was
copied; rendering is reimplemented natively in Jetpack Compose `Canvas`.

```
MIT License

Copyright (c) 2022 ELABBASSI Hicham

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
