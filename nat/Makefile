
DIR_JNI=/home/qq/labor/tools/jdk8/include

TARGET_EXE = gawain

#### Pleora eBus ####

ROOT_EBUS:=/home/lib/eBus

INC_EBUS := -I$(ROOT_EBUS)/include -I/usr/include/modbus \
	-D_UNIX_ -D_LINUX_ -DPV_GUI_NOT_AVAILABLE \
	-DPV_NO_GEV1X_PIXEL_TYPES -DPV_NO_DEPRECATED_PIXEL_TYPES

LIB_EBUS := -L$(ROOT_EBUS)/lib \
	-lPvBase \
	-lPvDevice \
	-lPvBuffer \
	-lPvGUIUtils \
	-lPvPersistence \
	-lPvGenICam \
	-lPvStreamRaw \
	-lPvStream \
	-lPvTransmitterRaw \
	-lPvVirtualDevice

#### Basler Pylon ####

ROOT_PYLON :=  /home/lib/pylon
ROOT_GENICAM := $(ROOT_PYLON)/genicam

INC_PYLON := -I$(ROOT_PYLON)/include \
	-I$(ROOT_GENICAM)/library/CPP/include \
	-DUSE_GIGE 

LIB_PYLON :=  -L$(ROOT_PYLON)/lib64 \
	-L$(ROOT_GENICAM)/bin/Linux64_x64 \
	-L$(ROOT_GENICAM)/bin/Linux64_x64/GenApi/Generic \
	-L$(ROOT_PYLON)/lib32 \
	-L$(ROOT_GENICAM)/bin/Linux32_i86 \
	-L$(ROOT_GENICAM)/bin/Linux32_i86/GenApi/Generic \
	-lpylonbase -lpylonutility \
	-lGenApi_gcc40_v2_3 -lGCBase_gcc40_v2_3 -lLog_gcc40_v2_3 -lMathParser_gcc40_v2_3 -lXerces-C_gcc40_v2_7_1 -llog4cpp_gcc40_v2_3 \
	-Wl,-E


#########################
####  configuration  ####
#########################

CC = g++ -DENABLE_LOG -O0 -std=c++11 -g -I. -I$(DIR_JNI) -I$(DIR_JNI)/linux

CC_FLAG += $(shell pkg-config --cflags opencv)
CC_FLAG += $(shell pkg-config --cflags libusb) 
CC_FLAG += $(shell pkg-config --cflags libmodbus)

LD_LIBS += $(shell pkg-config --libs opencv)
LD_LIBS += $(shell pkg-config --libs libusb) 
LD_LIBS += $(shell pkg-config --libs libmodbus)

####################################################

#OBJECTS = $(SOURCES:.cpp=.o)

LIB_SFRISO = ./SFR/FltrSlangEdge.cpp ./SFR/sfr_iso.c 

####################################################

TEST_PID := $(shell pgrep -f Gawain)

define BUILD_WORK =
	$(CC) -fPIC -shared -o $@ $^ $(LD_LIBS) $(CC_FLAG)
	@echo TEST_PID=$(TEST_PID)
	#cp -rf $@ ~/.gawain/$@
	mv $@ ~/.gawain/$@
	@echo 'Target --> '$@', done!!'
	@echo
endef

#ifdef ($(TEST_PID),) 
#	@echo "~~rewrite it~~"
#	cp -rf $@ ~/.gawain/$@
#else
#	@echo "!!!process is running!!!"
#endif

#####[core file]######

CORE_SO = libcore.so
CORE_FILE = ./vision/miscellany.cpp
CORE_FILE += ./running/stubber.cpp
CORE_FILE += ./running/work-1.cpp

$(CORE_SO): $(CORE_FILE)
	$(BUILD_WORK)

#####[capture device]######

VISION_SO = libvision.so
VISION_FILE  = ./vision/miscellany.cpp    
VISION_FILE += ./vision/CapVidcap.cpp  

$(VISION_SO): $(VISION_FILE)
	$(BUILD_WORK) 

#####[bridge device]######

BRIDGE_SO = libbridge.so
BRIDGE_FILE+= ./device/DevTTY.cpp
#TODO: we need to support Libusb again!!!
#BRIDGE_FILE+= ./device/DevUSB.cpp
BRIDGE_FILE+= ./device/DevModbus.cpp

$(BRIDGE_SO): $(BRIDGE_FILE)
	$(BUILD_WORK)

####################################################

TARGET_EXE = gawain
TARGET_FILE = ./running/work-2.cpp
TARGET_FILE += ./running/misc.cpp
TARGET_FILE += ./vision/miscellany.cpp

$(TARGET_EXE): $(TARGET_FILE)
	$(CC) -o $@ $^ $(CC_FLAG) $(LD_LIBS)
	@echo 'Build EXE --> '$@', done!!'
	@echo
	
####################################################

#all: $(TARGET_EXE) $(CORE_SO) $(VISION_SO) $(PERIPHERAL_SO) 
#all: $(TARGET_EXE)
#all: $(PERIPHERAL_SO) $(VISION_SO) $(CORE_SO)
all: $(BRIDGE_SO)

clean:
	rm -rf $(TARGET_EXE) $(TARGET_SO) $(DIR_SRC)/*.o ./*.o
	
.PHONY: all clean


