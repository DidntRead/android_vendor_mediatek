#include <binder/IBinder.h>
#include <nativebase/nativebase.h>
#include <ui/GraphicBufferMapper.h>
#include <ui/Rect.h>

extern "C" {
    void _ZN7android13GraphicBuffer4fromEP19ANativeWindowBuffer(ANativeWindowBuffer*);

    void _ZN7android13GraphicBufferC1EP19ANativeWindowBufferb(ANativeWindowBuffer* buffer, bool) {
        _ZN7android13GraphicBuffer4fromEP19ANativeWindowBuffer(buffer);
    }
}
