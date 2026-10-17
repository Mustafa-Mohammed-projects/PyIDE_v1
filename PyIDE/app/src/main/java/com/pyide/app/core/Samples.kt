package com.pyide.app.core

object Samples {
    val all = listOf(
        "hello.py" to """# Welcome to PyIDE
import sys, platform

name = input("What's your name? ")
print(f"Hello, {name}!")
print("Python", sys.version.split()[0], "on", platform.machine())

for i in range(1, 6):
    print(i, "squared =", i * i)
""",
        "opencv_demo.py" to """# First install it: Packages tab -> opencv-python
import numpy as np
import cv2

img = np.zeros((300, 480, 3), dtype=np.uint8)
img[:] = (30, 24, 16)
cv2.rectangle(img, (40, 40), (440, 260), (255, 124, 92), 3)
cv2.circle(img, (240, 150), 60, (211, 238, 34), -1)
cv2.putText(img, "OpenCV on Android", (70, 285),
            cv2.FONT_HERSHEY_SIMPLEX, 0.9, (255, 255, 255), 2)
cv2.imshow("demo", img)   # shown inside the IDE output panel
cv2.waitKey(0)
""",
        "matplotlib_demo.py" to """# First install it: Packages tab -> matplotlib
import numpy as np
import matplotlib.pyplot as plt

x = np.linspace(0, 10, 200)
plt.plot(x, np.sin(x), label="sin")
plt.plot(x, np.cos(x), label="cos")
plt.legend()
plt.title("Matplotlib")
plt.show()   # the chart appears in the output panel
""",
        "web_gui.py" to """# Run this, then open the Web tab (http://127.0.0.1:8000)
from http.server import HTTPServer, BaseHTTPRequestHandler

PAGE = ("<html><body style='font-family:sans-serif;text-align:center;padding:48px'>"
        "<h1>Hello from Python</h1><p>Served from inside the app.</p></body></html>").encode()


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.end_headers()
        self.wfile.write(PAGE)


print("Serving on http://127.0.0.1:8000 - press Stop to quit")
HTTPServer(("127.0.0.1", 8000), Handler).serve_forever()
""",
    )
}
