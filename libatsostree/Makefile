GCC := gcc
CLIBS := `pkg-config --libs ostree-1`
CINCLUDE := `pkg-config --cflags ostree-1`
CFLAGS := -fPIC -c
DEBUG_FLAGS := -g
LIBNAME := libatsostree.so

all: lib

%.o: %.c
	$(GCC) $(DEBUG_FLAGS) $(CFLAGS) $< $(CINCLUDE)

lib: *.o
	$(GCC) $(DEBUG_FLAGS) $< $(CLIBS) -shared -o $(LIBNAME) -Wl,-soname,atsostree

dist: lib
	mkdir -p linux-x86-64
	cp -v *.so linux-x86-64/

build-container:
	docker build -t "libatsostree:latest" .

debian:	build-container
	docker run --volume $(PWD)":/opt" "libatsostree:latest" make LIBNAME=libatsostree-debian.so

clean:
	rm -rf linux-x86-64
	rm -f *.o *.so
