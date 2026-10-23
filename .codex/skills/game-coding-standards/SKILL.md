---
name: game-coding-standards
description: Áp dụng kiến trúc và contract riêng của ST229 Cat Avatar Cat Maker khi viết, sửa, refactor hoặc review code Android, resource, test, navigation, data loading hay editor đồ họa trong repo này. Không dùng cho tài liệu hoặc chỉ quản lý artifact release.
---

# ST229 Cat Avatar Cat Maker

Đây là app/game Android một module, không phải Unity. Code chính nằm trong `app/src/main/java/com/oc/catemoji/catoc`; UI và resource nằm trong `app/src/main/res`.

## Contract cần giữ

- UI hiện tại là Activity-only. Screen feature kế thừa `BaseActivity<VB, VM>`; `MainActivity` chỉ dispatch tới Splash hoặc Home.
- `MyApplication.sharedViewModel()` tạo `ViewModelActivity` dùng chung qua một `ViewModelStore` cấp process. Không đổi nó thành ViewModel của từng Activity hoặc singleton khác nếu chưa phân tích toàn bộ luồng state.
- `ActivityRouter` nhận các `R.id.action_*` legacy nhưng thực tế mở Activity bằng `Intent`; repo không có Navigation Component graph. Giữ back-stack flag, extra và `TaskStackBuilder` contract của luồng cũ.
- Customized character chỉ lưu selection/transform dạng nhẹ; `listPath` được dựng lại từ template. Không lưu trùng catalogue vào `CustomizedCharacterDto`.
- Thứ tự vẽ (`zIndex`), thứ tự navigation, `SelectionIndex`, `templateId`, các entry đặc biệt `none`/`dice`, và ID `template_`/`online_` là contract dữ liệu; không suy diễn chúng chỉ từ vị trí list.
- Startup ưu tiên cache/local data rồi nạp các catalogue online song song. Không làm màn hình phụ thuộc vô điều kiện vào một request mạng.
- Giữ nguyên các tên legacy đang là package/path/API contract như `core/extention`, `cusomdiypet`, `keybroad`, JSON key và URL path. Chỉ migration khi task yêu cầu rõ và đã cập nhật tất cả caller/data tương thích.

## Chọn reference theo task

Chỉ đọc reference liên quan:

- Màn hình, ViewModel, Hilt, shared state, API/cache, model, persistence, navigation hoặc extension: đọc [architecture-and-data.md](references/architecture-and-data.md).
- Customize, DIY, Studio, Add Character, Cosplay/Show, Pause Challenge, custom view, bitmap, canvas, resource UI, ads, permission hoặc sharing: đọc [graphics-and-ui.md](references/graphics-and-ui.md).
- Viết test, chạy Gradle, review, thay model Gson/Parcelable, hoặc chuẩn bị bàn giao: đọc [verification.md](references/verification.md).

## Cách làm việc

1. Đọc lát cắt nhỏ nhất đủ hiểu task: file sở hữu logic, ViewModel/model liên quan, layout/resource đi kèm và implementation gần nhất khi cần đối chiếu. Không bắt buộc quét lại toàn repo cho mỗi thay đổi nhỏ.
2. Tìm trong `core/extention`, `core/helper`, `core/base`, `utils` và feature hiện tại trước khi tạo abstraction mới.
3. Đặt state và logic ở owner hợp lý: Activity render/forward event; ViewModel giữ UI state và coroutine; manager/repository/use case sở hữu data, I/O và mapping. Tôn trọ legacy boundary khi refactor rộng không nằm trong task.
4. Network, file I/O, bitmap decode/encode và tính toán nặng không chạy trên main thread. Không recycle bitmap khi nó còn được View, layer, cache hoặc Glide sở hữu.
5. Dùng resource thay cho text/màu/dimension hard-code trong UI. Không tự dịch chuỗi sang các locale nếu task không yêu cầu; để fallback có chủ đích và nêu rõ khi chỉ thêm chuỗi gốc.
6. Thêm hoặc cập nhật test khi thay đổi contract hay logic có thể kiểm chứng. Với thay đổi cơ học chỉ ở resource/style, chọn build hoặc smoke check phù hợp thay vì tạo test hình thức.
7. Giữ nguyên thay đổi không liên quan trong working tree, đặc biệt `.gradle`, `.idea`, `build/` và `app/release/` vì repo hiện có track một số artifact sinh tự động.

Khi bàn giao, nêu kiểm tra đã chạy, kiểm tra không thể chạy và rủi ro còn lại. Nếu buộc lệch contract trên, giải thích lý do cụ thể.
