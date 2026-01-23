from setuptools import setup, find_packages
from collections import OrderedDict
import os

long_description = """
Read more at the Battlecode website: https://play.battlecode.org.
"""

# set 'SETUPTOOLS_SCM_PRETEND_VERSION=' to manually set the version number when building

setup(name='battlecode26',
      description='Battlecode 2026 crossplay engine.',
      author='Battlecode',
      long_description=long_description,
      author_email='battlecode@mit.edu',
      url="https://play.battlecode.org",
      license='MIT',
      packages=find_packages(),
      package_data={
        "": ["../main/battlecode/world/resources/*.map26"]
      },
      project_urls=OrderedDict(()),
      install_requires=[
            'RestrictedPython==8.1',
            'flatbuffers==24.3.25'
      ],
      python_requires='>=3.12, <3.13',
      zip_safe=False,
      version='1.2.2',
)
