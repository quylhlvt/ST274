# Nhận diện tiếng sủa

## Danh sách từ theo ngôn ngữ

Các nước dùng chung ngôn ngữ được gộp trong cùng một hàng. Đây là các cách
viết mô phỏng âm thanh; không dùng động từ có nghĩa là sủa để cộng điểm.

| Ngôn ngữ / nước tiêu biểu | Âm thanh được so khớp |
| --- | --- |
| Việt Nam | gâu, gầu, gấu, gao, gô, gõ (so khớp gau, gao, go) |
| Tiếng Anh — Anh, Mỹ, Úc | woof, arf, ruff |
| Pháp | ouaf, ouah, wouf, waouh |
| Đức | wau, wuff |
| Tây Ban Nha, Mexico | guau |
| Hà Lan | waf |
| Thụy Điển | voff |
| Na Uy | voff |
| Đan Mạch | vov, vuf |
| Nga | gav, гав |
| Ukraine | hav, гав |
| Ba Lan | hau |
| Séc | haf |
| Hungary | vau |
| Hy Lạp | gav, ghav, γαβ |
| Thổ Nhĩ Kỳ | hav, hev |
| Tiếng Ả Rập | haw, hau, هاو |
| Tiếng Hebrew — Israel | hav |
| Tiếng Hindi — Ấn Độ | bho, भौं |
| Nhật Bản | wan, ワン, わん |
| Trung Quốc — tiếng Quan thoại | wang, wāng, 汪 |
| Hàn Quốc | mung / meong, wal, 멍, 왈 |
| Thái Lan | โฮ่ง |

Nguồn: [Duolingo — Animal sounds in different languages](https://blog.duolingo.com/animal-sounds-in-different-languages/),
[Babbel — A dog's bark in 19 languages](https://www.babbel.com/en/magazine/dogs-bark-other-languages).

`BarkWordMatcher` bỏ dấu chữ Latin, không phân biệt hoa/thường và nhận cả
dạng lặp có hoặc không có khoảng trắng: `gâu gâu`, `gaugau`, `wan-wan`,
`ワンワン`, `汪汪`, `멍멍` đều tính hai tiếng. Dấu lặp tiếng Thái trong `โฮ่งๆ`
cũng tính hai tiếng.

`sủa`, `sửa`, `sữa`, `sua`, `bark`, `barking`, `bellen`, `aboyer`, `latir`
không phải từ được cộng điểm. Hàm so khớp đếm từng token nằm trong danh sách,
kể cả khi nằm trong câu dài; không tìm từ con trong từ thông thường.
Đã khôi phục cách bỏ dấu ban đầu: `gâu`, `gầu`, `gấu` cùng thành `gau`;
`gạo` thành `gao`; `gô`, `gõ`, `gỗ` thành `go`.

Theo cấu hình được yêu cầu, `yap`, `yip`, `bau`, `hong`, `au`, `guk` cũng
đã được loại khỏi danh sách so khớp. Các biến thể có dấu như `bầu`, `Hồng`
và dạng lặp như `baubau`, `honghong`, `auau`, `gukguk` không được cộng điểm.

## Từ điển chữ và model nhận giọng nói

Đã khôi phục danh sách dùng chung của 23 ngôn ngữ, không lọc theo ngôn ngữ.
Ứng dụng vẫn đóng gói `vosk-model-small-vn-0.4`, với grammar:

```
gâu, gâu gâu, gầu, gầu gầu, gấu, gấu gấu,
gao, gao gao, gô, gô gô, gõ, gõ gõ, [unk]
```

Từ trong grammar đã được kiểm tra có trong từ điển model tiếng Việt.
Các dạng như `woof`, `guau`, `ouaf`, `wau`, `wan`, `wang` không có trong từ
điển model này nên không đưa vào grammar. Danh sách so khớp văn bản rộng hơn
khả năng nhận giọng của model. Nguồn: [Vosk model adaptation](https://alphacephei.com/vosk/adaptation).

## Cách cộng điểm đã khôi phục

- Cộng ngay từ partial, không chờ kết quả cuối và không lọc độ tin cậy.
- Màn Play mặc định chọn “Say gâu”. `txtBarkCount` hiển thị lần lượt từng
  điểm, cách nhau 80 ms, đồng bộ với hiệu ứng từng điểm. Nếu một lần nhận
  dạng tăng từ 3 lên 6, màn hình chạy 4 → 5 → 6; kết quả mới đến trong lúc
  hiển thị vẫn được cập nhật tiếp đến tổng mới nhất.
- Trong một câu chỉ cộng phần tăng thêm: partial `gâu` (+1), `gâu gâu`
  (+1), rồi final `gâu gâu` (+0). Kết quả lặp không được cộng lại.
- Vosk bật cả `setWords(true)` và `setPartialWords(true)`. Vị trí từ trong
  `partial_result` giúp phân biệt tiếng mới khi chữ partial không đổi:
  `gô` ở đoạn âm đầu (+1), `gô` ở đoạn âm tiếp theo (+1). Cùng một vị trí
  được gửi lại hoặc sửa ranh giới sẽ không được cộng lại.
- Vị trí từ chỉ dùng chống đếm trùng; không dùng khoảng nghỉ 600 ms, không
  lọc độ tin cậy hay đợi câu kết thúc. Dạng lặp của mọi từ trong grammar
  đều được khai báo, gồm `gô gô` và `gõ gõ`.
- Final kết thúc một câu và đặt số đã đếm trong câu về 0 để nhận câu mới.
- Giữ hành vi bản đầu: partial bị sửa giảm không trừ điểm đã cộng.
- Không kiểm tra khoảng nghỉ 600 ms, thời gian từng từ hay yêu cầu cả câu
  chỉ gồm tiếng sủa. Ví dụ `tôi nói gâu` được tính một tiếng.
- Vosk dùng endpoint mặc định của model. Dừng micro hoặc đổi chế độ sẽ
  giữ điểm đã cộng và không nhận thêm callback của phiên cũ.

Đây là việc khôi phục hành vi bản đầu theo yêu cầu, không phải kết luận
về độ chính xác nhận giọng nói trên mọi bản ghi.

## Chế độ TFLite

`DeviceSoundClassifier` lấy âm thanh PCM16 mono 44.100 Hz, dùng model
44.032 mẫu, suy luận xấp xỉ mỗi 200 ms. `BarkBurstScoreTracker` tách đợt âm
bằng frame RMS 20 ms và chỉ cộng điểm khi có model xác nhận.

Các điều kiện hiện giữ nguyên:

- Điểm lớp `1 sua` tối thiểu 0,85 ở hai cửa sổ hợp lệ.
- Cửa sổ xác nhận phải chứa toàn bộ đợt âm, cách nhau không quá 500 ms.
- Đợt âm dài từ 80 đến 600 ms, RMS đỉnh ít nhất 0,025.
- Cần hai frame giảm âm liên tiếp để đóng đợt âm.

Lớp `sua` trong `labels.txt` là tên lớp của model, không phải từ được tính
điểm ở chế độ văn bản; tên lớp này được giữ đúng với model.

Kết quả AI được lưu gần nhất 1.800 ms (tối đa 32 kết quả), để kết quả đến
trước khoảng lặng kết thúc đợt âm vẫn được sử dụng. Mỗi đợt âm chỉ cộng
một điểm, bất kể kết quả AI hay đợt âm được xử lý trước.

Log `BarkClassifier` có `bark`, `total`, `bursts`, `pending`, `hits`,
`rejectedDuration`, `rejectedVolume` để phân biệt model chưa đủ điểm,
chưa đủ xác nhận hay đợt âm bị loại vì độ dài/âm lượng. Cần bản ghi hoặc
log từ thiết bị để xác định trường hợp thực tế; unit test không đo được
độ chính xác nhận giọng người dùng.
