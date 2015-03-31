import sys

ignore_lines = ["Received", "Bytes sent", "PacketLength", "getPacket", "Starting", "getInverterData", "cmd="]

def print_byte(bytes, count):
   if (len(bytes) == 0):
      return ""
   if (bytes[0] != " "):
      return bytes[0] + print_byte(bytes[1:], count)
   else:
      if (count == 1):
         return " " + print_byte(bytes[1:], 4)
      else:
         return print_byte(bytes[1:], count - 1)

def print_bytes(bytes, received_seen):
   pointer = "<--" if received_seen else "-->"
   print pointer + " " + print_byte(bytes, 4)

in_filename = sys.argv[1]

print "Parsing " + in_filename + "..."
in_file = open(in_filename, "r")
in_data = in_file.read()
in_file.close()

pktbuf_seen = False
bytes_seen = False
received_seen = False
for in_line in in_data.split("\n"):
   if (in_line.startswith("<<<====== Content of pcktBuf =======>>>")):
      pktbuf_seen = True
   elif (in_line.startswith("<<<=================================>>>")):
      pktbuf_seen = False
   else:
      if (not pktbuf_seen):
         if (in_line.startswith("--------")):
            bytes_seen = True
            bytes = ""
         if (bytes_seen and not (in_line.startswith("---") or (in_line.startswith("000")))):
               bytes_seen = False
               print_bytes(bytes, received_seen)
               received_seen = False
         if (bytes_seen):
            if (not in_line.startswith("--------")):
               bytes += in_line[10:]
         else:
            if (in_line.startswith("Received")):
               received_seen = True
            if (not any([in_line.find(x) > -1 for x in ignore_lines])):
               print in_line
