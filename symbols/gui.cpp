#include <stdint.h>
#include <string>
#include <ui/GraphicBuffer.h>
#include <gui/BufferQueue.h>

extern "C" {

// GraphicBuffer(uint32_t inWidth, uint32_t inHeight, PixelFormat inFormat,
//               uint32_t inUsage, std::string requestorName = "<Unknown>");
void _ZN7android13GraphicBufferC1EjjijNSt3__112basic_stringIcNS1_11char_traitsIcEENS1_9allocatorIcEEEE(
    uint32_t inWidth, uint32_t inHeight, int inFormat, uint32_t inUsage,
    std::string requestorName);

//libcam_utils.so
void _ZN7android13GraphicBufferC1Ejjij(
    uint32_t inWidth, uint32_t inHeight, int inFormat, uint32_t inUsage) {
  std::string requestorName = "<Shim>";
  _ZN7android13GraphicBufferC1EjjijNSt3__112basic_stringIcNS1_11char_traitsIcEENS1_9allocatorIcEEEE(
      inWidth, inHeight, inFormat, inUsage, requestorName);
}

void _ZN7android11BufferQueue17createBufferQueueEPNS_2spINS_22IGraphicBufferProducerEEEPNS1_INS_22IGraphicBufferConsumerEEEb(android::sp<android::IGraphicBufferProducer>*, android::sp<android::IGraphicBufferConsumer>*, bool);
void _ZN7android11BufferQueue17createBufferQueueEPNS_2spINS_22IGraphicBufferProducerEEEPNS1_INS_22IGraphicBufferConsumerEEERKNS1_INS_19IGraphicBufferAllocEEE(android::sp<android::IGraphicBufferProducer>*, android::sp<android::IGraphicBufferConsumer>*);
  
void _ZN7android11BufferQueue17createBufferQueueEPNS_2spINS_22IGraphicBufferProducerEEEPNS1_INS_22IGraphicBufferConsumerEEERKNS1_INS_19IGraphicBufferAllocEEE(android::sp<android::IGraphicBufferProducer>* outProducer, android::sp<android::IGraphicBufferConsumer>* outConsumer) {
        _ZN7android11BufferQueue17createBufferQueueEPNS_2spINS_22IGraphicBufferProducerEEEPNS1_INS_22IGraphicBufferConsumerEEEb(outProducer, outConsumer, false);
}

void _ZN7android13GraphicBufferC1EjjijjjP13native_handleb(
        const native_handle_t* handle,
        android::GraphicBuffer::HandleWrapMethod method,
        uint32_t width,
        uint32_t height,
        int format,
        uint32_t layerCount,
        uint64_t usage,
        uint32_t stride);

void _ZN7android13GraphicBufferC1EjjijjP13native_handleb(
        uint32_t inWidth,
        uint32_t inHeight,
        int inFormat,
        uint32_t inUsage,
        uint32_t inStride,
        native_handle_t* inHandle,
        bool keepOwnership)
{
    android::GraphicBuffer::HandleWrapMethod inMethod =
        (keepOwnership ? android::GraphicBuffer::TAKE_HANDLE : android::GraphicBuffer::WRAP_HANDLE);
    _ZN7android13GraphicBufferC1EjjijjjP13native_handleb(inHandle, inMethod, inWidth, inHeight,
        inFormat, static_cast<uint32_t>(1), static_cast<uint64_t>(inUsage), inStride);
}

#ifdef __LP64__
    void _ZN7android18BufferItemConsumerC2ERKNS_2spINS_22IGraphicBufferConsumerEEEmib(const android::sp<android::IGraphicBufferConsumer>&, uint64_t, int, bool);

    void _ZN7android18BufferItemConsumerC1ERKNS_2spINS_22IGraphicBufferConsumerEEEjib(const android::sp<android::IGraphicBufferConsumer>& consumer, uint32_t consumerUsage, int bufferCount, bool controlledByApp) {
        _ZN7android18BufferItemConsumerC2ERKNS_2spINS_22IGraphicBufferConsumerEEEmib(consumer, static_cast<uint64_t>(consumerUsage), bufferCount, controlledByApp);
    }
#else
    void _ZN7android18BufferItemConsumerC2ERKNS_2spINS_22IGraphicBufferConsumerEEEyib(const android::sp<android::IGraphicBufferConsumer>&, uint64_t, int, bool);

    void _ZN7android18BufferItemConsumerC1ERKNS_2spINS_22IGraphicBufferConsumerEEEjib(const android::sp<android::IGraphicBufferConsumer>& consumer, uint32_t consumerUsage, int bufferCount, bool controlledByApp) {
        _ZN7android18BufferItemConsumerC2ERKNS_2spINS_22IGraphicBufferConsumerEEEyib(consumer, static_cast<uint64_t>(consumerUsage), bufferCount, controlledByApp);
    }
#endif

void _ZN7android12ConsumerBase7setNameERKNS_7String8E(const android::String8& name);

void _ZN7android18BufferItemConsumer7setNameERKNS_7String8E(const android::String8& name) {
    _ZN7android12ConsumerBase7setNameERKNS_7String8E(name);
}

} //extern "C"
