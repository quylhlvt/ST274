# Kiến trúc và dữ liệu

## Stack và phạm vi

- Một Android application module `:app`, package `com.oc.catemoji.catoc`.
- Kotlin là ngôn ngữ chính; một số integration WhatsApp và listener cũ là Java.
- Java/Kotlin target 11, `minSdk 24`, `targetSdk 36`, ViewBinding và DataBinding đều bật.
- Hilt, coroutines/Flow, Retrofit/OkHttp/Gson, Glide, SharedPreferences, MMKV, Firebase và LVT Ads là các dependency runtime chính.
- Không có Compose, Fragment hay Navigation Component graph trong luồng hiện tại.

## App shell và lifecycle

`MyApplication` kế thừa `AdsApplication` và là `@HiltAndroidApp`. Nó khởi tạo locale, SharedPreferences manager, MMKV, ads và một số resource warm-up.

`MainActivity` là launcher `singleTask`. Nó preload resource onboarding, dispatch tới `SplashActivity` hoặc `HomeActivity`, sau đó `finish()`.

`BaseActivity<VB, VM>` là contract chung của screen:

- Tạo shared `ViewModelActivity`, inflate binding và gọi `setupPreViews()`, `initView()`, `initText()`, `onViewCreated()` trong `onCreate()`.
- Sau khi root view được post, gọi `viewListener()`, `bindViewModel()`, `observeData()`.
- Ánh xạ lifecycle qua `onScreenStart()`, `onScreenStop()`, `onScreenDestroyed()`.
- Ghi Firebase screen view, ẩn system navigation và recreate khi locale thay đổi.
- Cung cấp các migration alias `viewLifecycleOwner`, `requireContext()`, `requireActivity()`, `isAdded`, `isDetached`. Chúng tồn tại để code chuyển từ Fragment tiếp tục chạy; không xem đó là bằng chứng repo vẫn dùng Fragment.

Khi thêm screen, bám theo Activity gần nhất và khai báo trong manifest. Không tạo Fragment/NavHost mới chỉ vì tên action ID trông giống Navigation Component.

## State ownership

`MyApplication` giữ một `ViewModelStore` riêng. `sharedViewModel(owner)` dùng factory/extras của Activity nhưng trả về cùng một `ViewModelActivity` cho mọi screen trong process.

`ViewModelActivity` là facade state dùng chung:

- Expose catalogue, customized character, background, sticker, speech, design path và loading/error từ `AppDataManager`.
- Giữ readiness state cho local data, onboarding image, DIY catalogue, background/sticker và Studio catalogue.
- Giữ một số state chuyển tiếp giữa screen như bitmap cosplay/customize và cờ restart.
- Guard fetch online để không request trùng và retry một số catalogue khi network trở lại.

Feature ViewModel sở hữu state của một flow/screen. Pattern ưu tiên:

```kotlin
private val _state = MutableStateFlow(FeatureState())
val state: StateFlow<FeatureState> = _state.asStateFlow()
```

Collect trong Activity bằng `lifecycleScope` + `repeatOnLifecycle(Lifecycle.State.STARTED)` trừ khi code gần đó có lý do khác. Dùng `SavedStateHandle` cho draft/UI selection cần sống qua recreation; không đẩy bitmap lớn vào Bundle/SavedStateHandle.

## Data flow và cache

`AppDataManager` là owner chính của catalogue và dữ liệu người dùng. Nó expose read-only `StateFlow`, đọc asset/local file, merge API template, lưu API cache, customized character và design path.

Luồng khởi động trong `ViewModelActivity`:

1. Thử quick/cache data trên IO.
2. Fallback sang initial local data nếu cache không hợp lệ.
3. Đánh dấu local data ready.
4. Nạp template API, DIY manifest/images, background/sticker manifest và Studio catalogue song song.

Giữ app có thể hoạt động từ cache/local khi remote chậm hoặc lỗi. Khi thêm remote dataset, cung cấp timeout, cache/fallback hợp lý, guard request trùng và readiness/error state mà UI có thể quan sát.

`ApiHelper` có hai Retrofit service cho hai base URL. `RemoteDataSource`/mapper có trách nhiệm fallback và chuyển response thành model app. Không đưa parsing URL/JSON rải rác vào Activity mới.

## Model và compatibility

Model runtime thực tế nằm trong `data/model/custom/ModelNew.kt`; các file `CustomModel.kt`, `BodyPartModel.kt`, `ColorModel.kt` có thể chỉ còn code comment legacy. Kiểm tra declaration thực trước khi thêm type trùng.

Invariant quan trọng:

- `CustomModel` đại diện cả template lẫn customized character.
- Template được nhận diện bằng ID `template_` hoặc `online_`.
- Customized character giữ `templateId`, `SelectionIndex`, `LayerTransform`, preview path và timestamp.
- `CustomizedCharacterDto` không lưu `listPath`; khi load phải gắn catalogue từ template phù hợp.
- Selection được validate với catalogue hiện tại. Catalogue thay đổi có thể làm index cũ invalid; không truy cập bằng `!!` hoặc index không clamp/validate.
- Các layer thường được sort theo `zIndex` để render; một số gameplay như Pause Challenge có navigation order riêng.
- `none` và `dice` là sentinel path. Mapper chèn chúng theo body-part rule; code chọn asset thật phải loại hai giá trị này.
- `LayerTransform.isDefault()` dùng epsilon để chịu sai số Float; không đổi sang so sánh bằng tuyệt đối.

Khi thay field Gson/Parcelable hoặc tên model, kiểm tra JSON cũ, `@SerializedName`, DTO conversion, Parcelize caller và R8 rules.

## Navigation và Intent

`ActivityRouter` nằm cuối `BaseActivity.kt`. Nó map các ID trong `res/values/ids.xml` sang Activity class, chuyển `Bundle` thành extras, và áp dụng back-stack flags cho Home/MyPony. Luồng tạo character sang album dùng `TaskStackBuilder` để Back trở về Home.

Ưu tiên constant extra trong `companion object` của Activity sở hữu contract. Khi đổi extra, cập nhật cả producer, consumer, restart/edit flow và instrumentation test. Không tự đổi `CLEAR_TOP`, `SINGLE_TOP`, `NEW_TASK` hay `CLEAR_TASK` nếu chưa kiểm tra back stack thực tế.

## Vị trí code và tái sử dụng

- `core/base`: base Activity, adapter và dialog.
- `core/custom`: `DrawView`, layer/draw object, crop và listener đồ họa.
- `core/extention`: extension hiển thị, click, image, ads, navigation, permission, network và system UI.
- `core/helper`: bitmap, download, JSON file, locale, network monitor, permission, rating, preference, string/typeface, wallpaper.
- `data/datalocal`: API, DI module và manager.
- `data/model`: model/DTO/mapper theo domain.
- `data/repository`, `data/usecase`: truy cập và orchestration dữ liệu.
- `ui/<feature>`: Activity, ViewModel, adapter và state của feature.
- `utils/key`: key legacy dùng chung; kiểm tra caller trước khi tạo key mới.

Dùng `BaseAdapter` nếu feature gần đó đang theo pattern mutable `items`; dùng `ListAdapter`/DiffUtil khi danh sách cần diff/payload hoặc code gần đó đã dùng nó. Không bắt buộc migration toàn bộ adapter trong một task không liên quan.

Quy ước mới theo Kotlin official style: type `PascalCase`, member `lowerCamelCase`, constant `UPPER_SNAKE_CASE`, mutable state có backing `_state`. Tôn trọ style của file khi sửa cục bộ; không dùng task nhỏ để rename hàng loạt legacy spelling/package.
