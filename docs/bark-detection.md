# Nhận âm giả tiếng sủa trong Play và Training

## Luồng hiện tại

Play và Training dùng chung `BarkDetectionManager`, không chuyển giọng thành chữ,
không nhận diện ngôn ngữ, không yêu cầu nói “gâu/go/gô”. Người chơi giả tiếng sủa
như “woof” bằng giọng của mình. Độ lớn chỉ giúp tách sự kiện, không tự cộng điểm.

- Microphone tích hợp, PCM16 mono 16 kHz, mỗi khối 20 ms. Native buffer giữ ít nhất
  500 ms để quá trình phân loại không làm mất các tiếng tiếp theo trên máy chậm.
- `BarkEventSegmenter` tìm sự kiện dài 60–650 ms; khoảng nghỉ 60 ms chốt một sự kiện.
  Giữ một khối trước onset, bỏ tiếng click quá ngắn và âm giữ liên tục quá dài.
- `BarkAudioClassifier` đệm sự kiện ở giữa cửa sổ 15.600 mẫu, lấy embedding YAMNet
  1.024 chiều, rồi dùng RBF head phân biệt người giả sủa với lời nói/âm khác.
  Head là nhị phân, không có danh sách từ hay ngôn ngữ. Ngưỡng margin cố định 0,85.
- Mỗi sự kiện được chấp nhận cộng một điểm ngay sau khoảng nghỉ và inference.
  Không chờ chốt câu và không có cooldown sau một lần nhận dạng. Một chuỗi sủa
  cách nhau ít nhất 60 ms có thể cộng từng điểm liên tiếp.
- Hai màn dùng cùng model và chính sách. `StateFlow` giữ tổng điểm nên nhiều lần
  cập nhật liên tiếp không làm mất delta; UI tiêu thụ từng điểm để phát hiệu ứng.
  Renderer giới hạn 12 hiệu ứng đồng thời để giữ bộ nhớ ổn định.
- Play chặn điểm trước START, khi mở dialog, khi screen dừng và sau game over.
  Đối thủ chỉ chạy khi recorder đã sẵn sàng (`LISTENING`), không chạy lúc nạp model.
  Training dừng mic khi mở hướng dẫn và tiếp tục khi đóng hướng dẫn.
- Hủy phiên tăng session ID, hủy job và đóng recorder/model; callback phiên cũ không
  được cộng điểm. Khóa capture dùng chung ngăn Play/Training mở mic chồng nhau.

Vosk/JNA, bộ đếm từ tiếng Việt, bộ xử lý PCM dành riêng cho Vosk, model và fixture
chẩn đoán cũ đã được xoá. Debug chỉ còn Activity trống để giữ foreground khi test;
Activity này không tự thu hoặc lưu âm thanh.

Hướng dẫn voice mới chỉ có bản tiếng Anh; các locale fallback về bản gốc cho key
này để không còn yêu cầu nói tiếng Việt hoặc chấm theo độ lớn.

## Model, dữ liệu và giới hạn

Model và head tổng cộng khoảng 10,8 MiB. Nguồn và giấy phép nằm trong
`app/src/main/assets/audio/NOTICE.txt`; hash và thông số trong `model-card.json`.
Head dùng 4.606 sự kiện train từ Vocal Imitation Set, VocalSketch, Speech Commands
và năm bản ghi tiếng gõ bàn/gỗ. Số này gồm 234 sự kiện tiếng gõ ở nhiều mức âm lượng
và 1.745 biến thể giả sủa có thay đổi âm lượng hoặc phản xạ trong phòng.
Không dùng bản ghi microphone của người dùng để huấn luyện hoặc đóng gói.

Validation tách theo mã speaker trong từng bộ dữ liệu bằng SHA-256; cùng mã không
xuất hiện ở hai phía. Không có ánh xạ danh tính giữa hai bộ dữ liệu khác nhau.
Có 84 sự kiện từ clip dương và 557 sự kiện âm/lời nói đối chứng. Bản mới nhận
41/84 sự kiện dương (trước đó 38/84), với 16/557 sự kiện đối chứng bị nhận nhầm
ở cả hai bản. Trên 381 biến thể âm lượng của các clip dương, bản mới nhận 200,
so với 185 ở bản cũ. Trên ba bản ghi gõ bàn giữ riêng, bản cũ nhận nhầm 1/41
sự kiện ở nhiều mức âm lượng, bản mới nhận nhầm 0/41.
Nhãn sự kiện kế thừa nhãn cả clip, chưa căn thủ công từng tiếng: các clip giả sủa
cũng chứa tiếng thở/click. Vì thế không được coi tỷ lệ trên là recall từng tiếng
sủa đã xác minh. Validation đã dùng để chọn model/ngưỡng, không phải blind test.

Model này sửa phụ thuộc tiếng Việt và cách cập nhật theo cả câu; chưa chứng minh
nhận chính xác mọi kiểu “woof” hoặc loại hết lời nói. Những âm ngắn, gần tiếng sủa
vẫn có thể bị nhận nhầm. Âm sủa quá nhỏ, kéo dài, chồng lấn tiếng nói hoặc không có
khoảng nghỉ 60 ms có thể bị bỏ sót. Cần thêm dữ liệu giọng thật đa dạng và nhãn từng
sự kiện trước khi coi chất lượng nhận dạng đã đạt yêu cầu phát hành rộng.

## Kiểm tra hồi quy

- Unit: RBF head (distance, dấu coefficient, dữ liệu không hợp lệ), segmentation
  20 tiếng liên tiếp, tiếng click/âm giữ dài và nền ổn định.
- Android: 4 tiếng người giả sủa từ 4 speaker nằm ngoài tập train, lặp mỗi mẫu
  10 lần với khoảng nghỉ 60 ms; một mẫu giả sủa bản cũ bỏ sót; câu nói thường
  tổng hợp Anh/Đức/Pháp/Tây Ban Nha;
  im lặng và ba bản ghi tiếng gõ bàn giữ riêng ở năm mức âm lượng. Các fixture
  giả sủa được chọn rõ tiếng để kiểm tra runtime và đếm sự kiện,
  không phải phép đo độ chính xác trên mọi người. Provenance trong assets test.
- Android: mic tích hợp mở/đọc/hủy/mở lại; Training và Play cộng điểm/phát hiệu ứng,
  không tăng do level cao đơn thuần, không replay hiệu ứng cũ sau recreate, và
  không chấm điểm khi trận bị tạm dừng/kết thúc.

## Tái tạo model

Xem `tools/audio/README.md`. `train_bark_head.py` xuất embedding/head và model card,
cho phép dùng lại feature cache có SHA-256 để đối chiếu đúng lần huấn luyện này.
