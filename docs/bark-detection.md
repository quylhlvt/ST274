# Nhận tiếng sủa trong Play và Training

- Các từ được tính điểm: `gâu`, `go`, `gô`, không phân biệt hoa/thường,
  chuẩn hóa Unicode NFC. Không bỏ dấu hàng loạt: `gỗ`, `gõ`, `gấu`, `gầu`,
  `gạo`, `mõ`, `vàng`, `giàng` không nằm trong danh sách tính điểm.
- Cộng ngay lần đầu một từ hợp lệ xuất hiện trong best-path partial hoặc
  transcript. Không đợi confidence, mốc thời gian, hai kết quả giống nhau,
  khoảng nghỉ hay kết thúc câu. Metadata từ đến muộn không chặn partial mới.
- Partial lặp và final của cùng câu không cộng lại; vị trí từ mới giúp đếm
  từng tiếng liên tiếp khi chữ partial không đổi.
- Grammar chỉ có `gâu`, `go`, `gô` để người dùng giả tiếng sủa. Không thêm
  từ đối chứng hoặc `[unk]` vào danh sách nhận dạng.
- Microphone đọc PCM16 mono 16 kHz theo khối 40 ms. UI cập nhật điểm và
  hiệu ứng khi có điểm mới, không có hàng đợi hiển thị cách nhau 80 ms.
- Dừng màn hủy phiên microphone; callback của phiên cũ không cộng điểm.

Model vẫn có thể nghe nhầm tùy giọng và môi trường. Mẫu âm thanh tổng hợp
và test logic không thay thế việc kiểm tra với giọng người dùng trên thiết bị.

API: [Vosk Recognizer](https://github.com/alphacep/vosk-api/blob/master/java/lib/src/main/java/org/vosk/Recognizer.java).

Play và Training chỉ dùng Vosk để nhận giọng nói tiếng Việt, cùng chính sách
đếm từ ở trên. Model, thư viện và nhánh phân loại âm thanh TFLite đã được gỡ.
