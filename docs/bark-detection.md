# Nhận tiếng sủa trong Play và Training

- Các từ được tính điểm: `gâu`, `go`, `gô`, không phân biệt hoa/thường,
  chuẩn hóa Unicode NFC. Không bỏ dấu hàng loạt: `gỗ`, `gõ`, `gấu`, `gầu`,
  `gạo`, `mõ`, `vàng`, `giàng` không nằm trong danh sách tính điểm.
- Cộng ngay lần đầu một từ hợp lệ xuất hiện trong best-path partial hoặc
  transcript. Không đợi confidence, mốc thời gian, hai kết quả giống nhau,
  khoảng nghỉ hay kết thúc câu. Metadata từ đến muộn không chặn partial mới.
- Partial lặp và final của cùng câu không cộng lại; vị trí từ mới giúp đếm
  từng tiếng liên tiếp khi chữ partial không đổi.
- Sau ít nhất 0,8 giây, luồng nhận dạng chốt kết quả ở khoảng nghỉ ngắn giữa
  các tiếng và đặt lại decoder; nếu không có khoảng nghỉ thì chốt sau tối đa
  1,2 giây. Tránh cắt giữa một tiếng vì nó có thể bị cộng ở cả hai phiên.
  Kết quả chốt cùng partial trước đó không được cộng lại; phiên kế tiếp có
  thể đếm một tiếng `gâu/gô/go` mới dù chữ partial giống hệt phiên trước.
- Grammar gồm `gâu`, `go`, `gô`, nhánh loại âm lạ `[unk]` và các từ dễ nhầm
  `mõ/vàng/giàng`. Chỉ ba từ đầu tính điểm; các nhánh còn
  lại giúp tránh ép mọi tạp âm hoặc từ gần âm thành từ tính điểm.
- Microphone đọc PCM16 mono 16 kHz theo khối 40 ms. UI cập nhật điểm và
  hiệu ứng khi có điểm mới, không có hàng đợi hiển thị cách nhau 80 ms.
- Dùng `AudioSource.MIC`, ưu tiên `TYPE_BUILTIN_MIC` để thu trực tiếp từ điện
  thoại kể cả khi kết nối tai nghe. Bật NoiseSuppressor và AcousticEchoCanceler
  theo audio session nếu thiết bị hỗ trợ; lỗi effect không làm ngừng thu.
- PCM được lọc thông cao 80 Hz để giảm DC/tiếng ù, tăng gain tối đa 3 lần có
  làm mượt cho giọng nhỏ nổi trên nền nhiễu thích nghi. Không tăng gain cho nền
  liên tục, không đóng cổng âm lượng cứng làm mất đầu tiếng sủa,
  không tính điểm theo độ lớn âm thanh. Meter hiển thị RMS trước xử lý phần mềm.
- Lọc nhiễu phổ bằng cửa sổ chồng lấn 256 mẫu, tự theo dõi nền nhiễu ngay
  trong phiên thu và giữ sàn mềm để không cắt mất tiếng nhỏ. Xử lý phần mềm
  thêm 16 ms độ trễ tại 16 kHz, không cần khoảng dừng để hiệu chuẩn và không
  phụ thuộc vào việc thiết bị có hỗ trợ NoiseSuppressor của Android hay không.
- Giữ `partial_result` và vị trí từng từ để tiếng sủa liên tiếp có cùng partial
  text vẫn được đếm; partial và final của cùng tiếng không cộng trùng.
  Mỗi lần đọc lấy best-path với `setPartialWords(false)` trước, rồi lấy
  alignment với `setPartialWords(true)`; không dùng alignment làm điều kiện
  chờ trước khi tính điểm.
- Dừng màn hủy phiên microphone; callback của phiên cũ không cộng điểm.
  Khóa phiên dùng chung giữa Play/Training, giải phóng recorder và audio effect
  trước khi phiên kế tiếp mở mic.

Model vẫn có thể nghe nhầm tùy giọng và môi trường. Đây là nhận dạng giọng
giả sủa `gâu/go/gô`, chưa phải model phân loại mọi kiểu gầm/sủa phi ngôn ngữ.
Khử nhiễu của Android phụ thuộc thiết bị; tiếng nói chồng lấn hoặc nhiễu lấn át
giọng vẫn có thể làm mất tiếng sủa. Mẫu âm tổng hợp trộn nhiễu và test logic
không thay thế việc kiểm tra với giọng người dùng trên thiết bị.

## Giới hạn đã đo trên microphone thật

Vosk là model nhận dạng tiếng Việt tổng quát, không phải model được huấn luyện
để phân biệt riêng `gâu/gô/go` với các từ khác cùng âm lượng. Trên bản ghi thử
30 giây từ microphone tích hợp, cấu hình trước khi chia phiên cộng nhầm 2 điểm
trong đoạn nói thường (khoảng 6,8 và 8,48 giây), và cộng 4 điểm trong đoạn giả
tiếng sủa. Chốt theo khoảng nghỉ từ 0,8 giây tăng đoạn sủa lên 6 điểm trên
cùng bản ghi, còn đoạn nói thường vẫn bị cộng nhầm 2 điểm.
Vì vậy chưa thể coi khả năng chống nhận nhầm là đạt yêu cầu. Ngưỡng âm lượng
hay chỉ thu hẹp grammar làm lỗi nặng hơn trên cùng bản ghi.

Assets chỉ đóng gói bản ZIP 32 MiB của model; bản giải nén trùng lặp khoảng
51 MiB đã được bỏ. Lần chạy đầu giải nén ZIP vào bộ nhớ riêng của app để Vosk
đọc offline. Muốn giảm tiếp dung lượng và tăng độ chính xác cần bộ mẫu dương
`gâu/gô/go` cùng bộ mẫu âm dễ nhầm, thu từ nhiều người và nhiều môi trường, để
huấn luyện và kiểm thử model từ khóa nhỏ trước khi thay Vosk.

API: [Vosk Recognizer](https://github.com/alphacep/vosk-api/blob/master/java/lib/src/main/java/org/vosk/Recognizer.java).
Microphone: [AudioRecord](https://developer.android.com/reference/android/media/AudioRecord),
[NoiseSuppressor](https://developer.android.com/reference/android/media/audiofx/NoiseSuppressor).

Play và Training hiện dùng chung Vosk và chính sách đếm từ ở trên. Model TFLite
thử nghiệm không nằm trong luồng cộng điểm của bản phát hành.
