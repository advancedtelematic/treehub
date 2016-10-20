JAVA_HOME := /usr/lib/jvm/default
GCC := gcc
CLIBS := -lglib-2.0 -lgio-2.0 -lgobject-2.0 -lostree-1
CINCLUDE := -I /usr/lib/glib-2.0/include -I /usr/include/glib-2.0/ -I /usr/include/ostree-1
CFLAGS := -fPIC -c
DEBUG_FLAGS := -g

all: lib

%.o: %.c
	$(GCC) $(DEBUG_FLAGS) $(CFLAGS) $< -I $(JAVA_HOME)/include -I $(JAVA_HOME)/include/linux $(CINCLUDE)

lib: *.o
	$(GCC) $(DEBUG_FLAGS) $< $(CLIBS) -shared -o libatsostree.so -Wl,-soname,atsostree

clean:
	rm -f *.o *.so
