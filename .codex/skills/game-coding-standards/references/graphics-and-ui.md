# Editor đồ họa, bitmap và UI

## Bản đồ feature

### Customize

`ui/main/customize` ghép character từ catalogue template:

- `CustomizeViewModel` sở hữu template, selection theo layer, flip, transform và draft trong `SavedStateHandle`.
- Body part render theo `zIndex`; selection phải resolve an toàn qua color/path index.
- Save tạo customized character mới hoặc cập nhật bản cũ mà không nhét lại `listPath` vào DTO.
- Reward/interstitial và analytics nằm trong luồng UX hiện tại; giữ callback `onNextAction`/earned contract khi thay navigation.

### DIY Pet

`ui/main/cusomdiypet` là editor canvas nhiều loại layer:

- `DrawView` giữ `drawList`, selected draw, matrix, icon thao tác, undo snapshot và touch handling.
- `CustomDiyPetViewModel` build catalogue, dedupe image request, giới hạn decode song song, cache bitmap canvas và render text ngoàn main thread.
- `DiyBitmapRenderer` là CPU-only, không phụ thuộc Activity/View lifecycle.
- Keyboard có tối đa 36 image key, bốn hàng nhãn, sentinel `DELETE_KEY`, text layer dùng chung và quan hệ linked image/text. Xóa phải đúng instance ngay cả khi ký tự lặp.
- Catalogue có fallback count cho eye/key/tag khi manifest thiếu. Không xóa fallback nếu chưa thay contract offline.

### Cat Studio

`ui/main/studio` chọn asset theo catalogue Studio và thêm `DrawableDraw` vào canvas. `CatStudioViewModel` giữ danh sách draw, path-to-location, selected draw, scroll state và draft matrix values. Khi restore, phải giữ đúng matrix/layer order và selection mapping.

### Add Character

`ui/main/add_character` trang trí kết quả bằng background image/color, sticker, speech và text. Nó dùng Photo Picker contract hiện đại. Giữ phân biệt `DesignSource`, background type và navigation type khi save/edit.

### Cosplay / Show

Cosplay chọn target; `ShowViewModel` giữ target selection và user selection riêng, tính phần trăm match và emit complete event. Không trộn thứ tự vẽ với thứ tự selection đã remap.

### Pause Challenge

`PauseChallengeSession` có rule riêng:

- Stage navigation độc lập với stacking order.
- Với URL catalogue thật, lấy `navN` từ tên file; nếu không có thì fallback `zIndex`, không parse con số trong host/thư mục.
- Choice loại path rỗng, `none`, `dice`.
- Chỉ commit frame sau khi ảnh đã hiển thị; skip chỉ xóa layer hiện tại.
- Export/Next giữ các layer đã confirm, không vô tình lấy layer đang chạy chưa confirm. Try Again phải tạo session sạch cho cùng template.

## `DrawView` và render loop

`DrawView` kế thừa `FrameLayout` và vẽ layer trong `dispatchDraw()`. Nó đã cache nhiều `Path`, `Paint`, `RectF`, `Matrix`, `FloatArray` và `PointF` để giảm allocation.

Khi sửa custom drawing:
- Không tạo bitmap, drawable, list, regex, string lớn hoặc object hình học lặp lại trong `dispatchDraw`, `onDraw`, touch move, animation tick hay scroll callback nếu có thể cache/tái sử dụng.
- Không I/O, network hoặc decode trong render/touch callback.
- Tái sử dụng matrix/point buffer khi logic chạy mỗi frame. Nếu thêm cache, reset nó khi draw/size/scale owner thay đổi.
- Giữ minimum-scale constraint, hit testing, layer ordering, selection border/icon và undo semantics.
- Hủy callback/handler/listener khi View detach hoặc screen destroy nếu callback có thể giữ Activity/View.

## Quyền sở hữu bitmap

Xác định owner trước khi `recycle()`:

- Bitmap do Glide trả về có thể thuộc pool. Nếu cần giữ sau `Glide.clear(request)`, copy sang bitmap app sở hữu trước.
- Bitmap đang được `DrawableDraw`, View, `LruCache` hoặc shared state tham chiếu không được recycle sớm.
- `CustomDiyPetViewModel` evict cache bằng cách bỏ cache reference, không recycle layer bitmap dùng chung.
- Snapshot tạm do caller chuyển quyền sở hữu vào hàm save có thể được recycle trong `finally`; nêu rõ contract này trong tên/doc của API.
- Cache nặng cần bounded (`LruCache`/giới hạn số request). Xét `ActivityManager.isLowRamDevice` trước preload không thiết yếu.
- Encode/decode/file save chạy trên IO; text/layout/trim pixel CPU nặng chạy trên Default; chỉ chạm View/cache bookkeeping trên Main khi cần.

## UI, resource và responsive layout

- Tái sử dụng layout include, drawable, color, dimen, font và style hiện có trước khi tạo mới.
- `values-sw600dp` có override cho tablet. Thay dimension/layout quan trọng phải kiểm tra cả phone và tablet; grid count còn phụ thuộc `MyApplication.isTablet`.
- Base string có nhiều key hơn các locale `de`, `es`, `fr`, `hi`, `in`, `pt`; Android fallback sang base là hành vi hiện tại. Không bịa bản dịch. Khi task có localization, giữ placeholder/format token và cập nhật các locale được yêu cầu.
- Dùng `View.onClick()`/`onClick2()` nếu screen gần đó dùng debounce chung. Hai extension này dùng timestamp global, nên click liên tiếp trên hai control khác nhau cũng có thể bị chặn; test và flow mới phải tính đến contract này.
- Dùng Activity Result API cho photo picker/permission mới thay vì `startActivityForResult`.

## Ads, permission và sharing

Ads là một phần của navigation contract:

- `showInter { ... }` chỉ chạy action tiếp theo khi callback tới và Activity còn sống.
- Reward chỉ grant khi callback earned đã xảy ra; close/fail không được vô tình grant.
- Native/collapsible ad view phải được block/remove theo lifecycle pattern của screen gần nhất.
- Không nâng Ads SDK/mediation hoặc đổi ID khi task không yêu cầu.

Permission phụ thuộc API level trong `PermissionHelper`; storage legacy bị giới hạn bởi `maxSdkVersion` trong manifest. Dùng MediaStore/FileProvider và grant URI permission đúng pattern hiện có khi download/share. WhatsApp sticker provider có authority và read permission cố đị; thay package/provider cần kiểm tra integration bên ngoài, không chỉ compile app.
