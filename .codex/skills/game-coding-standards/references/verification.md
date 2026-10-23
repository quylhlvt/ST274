# Kiểm thử và bàn giao

## Chọn mức kiểm tra

Chạy kiểm tra tỷ lệ với rủi ro:

- Logic/model/state thuần Kotlin: thêm hoặc cập nhật local unit test trong `app/src/test` và chạy test class/task liên quan.
- Activity, lifecycle, canvas, click, Intent/back-stack hoặc render bitmap: dùng instrumentation test trong `app/src/androidTest` khi behavior không thể tách thành unit test.
- XML/resource/view binding: chạy build resource/compile phù hợp; test logic chỉ khi có behavior thật.
- Thay Gson/Parcelable/R8/network model: chạy unit test mapping/round-trip nếu có, build debug và cân nhắc release/minified build vì `release` bật `minifyEnabled` và `shrinkResources`.

Lệnh phổ biến:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew connectedDebugAndroidTest
git diff --check
```

Chỉ chạy `connectedDebugAndroidTest` khi có emulator/device phù hợp. Với instrumentation test dùng click UI, lưu ý `View.onClick()` debounce chung 200 ms; test hiện tại chờ trước click liên tiếp.

## Coverage hiện có

Local unit tests có giá trị tham chiếu:

- `CustomModelEditValidationTest`: compatibility của saved selection với catalogue hiện tại, z-index ordering, color/path bị xóa, partial legacy selection.
- `LayerTransformTest`: epsilon và round-trip transform.
- `DiyCatalogueTest`: fallback catalogue, keyboard rows, delete key, category/path mapping.
- `PauseChallengeSessionTest`: navigation order, sentinel filtering, random choice, freeze/skip/complete.

Instrumentation tests:

- `DiyKeyboardDeletionTest`: tag catalogue, keyboard/text placement, transparent padding, repeated-character deletion, linked layer deletion và sort/list consistency.
- `PauseChallengeDoneFlowTest`: confirmed-layer export, pause preview, auto-complete, Done/Try Again/back-stack flow.

Khi sửa các domain này, mở test tương ứng trước khi viết test trùng. Khi tạo logic thuần mới, ưu tiên tách khỏi Activity/View để unit test được.

## Checklist theo rủi ro

### State và lifecycle

- Collector có lifecycle owner và không bị nhân đôi sau recreation.
- Job/request lặp có guard/cancel; callback không giữ Activity/View đã destroy.
- Shared state thật sự cần process scope; screen-local state không bị đẩy vô cớ vào `ViewModelActivity`.
- Draft cần restore được lưu bằng ID/index/matrix nhẹ, không phải bitmap lớn.

### Data và network

- Offline/cache flow vẫn hoạt động khi remote lỗi.
- Request trùng, timeout, retry và fallback URL không bị phá.
- Catalogue merge không nhân bản template hoặc làm mất customized character.
- Selection/path được validate trước khi index; sentinel `none`/`dice` không bị load như URL.
- Thay model serialization đã xem `proguard-rules.pro`, DTO converter và backward compatibility.

### Graphics và media

- Không I/O/decode/encode trên main; không allocation lớn trong render/touch loop.
- Bitmap owner rõ ràng; không recycle pooled/shared/visible bitmap; snapshot tạm được release.
- Layer order, selection mapping, matrix/flip/scale/translation/rotation và undo/restore được giữ.
- Export ảnh không còn selection border/icon, không cắt sai tỷ lệ và tồn tại ở path mà Intent/album sẽ đọc.

### UI và navigation

- Screen mới có manifest entry, binding đúng layout và theo thứ tự hook của `BaseActivity`.
- Back/Up, ad callback, Intent extra, `CLEAR_TOP`/`SINGLE_TOP` và Try Again/edit flow không hồi quy.
- Phone/tablet, locale thay đổi, immersive navigation, keyboard `adjustResize` và permission API level được xem xét khi liên quan.
- Text hiển thị dùng resource; không phá placeholder; không tự tạo bản dịch không được cung cấp.

## Working tree và artifact

Repo đang track một số file trong `.gradle`, `.idea`, `build/reports` và `app/release`. Build có thể làm chúng thay đổi. Trước và sau khi chạy Gradle:

- Ghi nhận `git status --short`.
- Không revert/xóa artifact hoặc thay đổi của người dùng không thuộc task.
- Không package APK, tăng version, thay signing, nâng dependency hay chỉnh release metadata nếu task không yêu cầu.
- Phân biệt file do thay đổi code tạo ra với file đã dirty từ trước.

## Bàn giao

Nêu ngắn gọn:

- Behavior/contract đã thay đổi.
- Test/build/smoke check đã chạy và kết quả.
- Kiểm tra không thể chạy do thiếu device/network/thời gian.
- Rủi ro còn lại hoặc migration tương thích cần lưu ý.
