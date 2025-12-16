# Nacos GitHub Dependents Scraper

A Python script that scrapes and exports the list of repositories dependent on alibaba/nacos from its GitHub dependencies page.

## Features

- 🌐 Automated web scraping using Selenium
- 📄 Handles pagination automatically to traverse all pages
- 💾 Exports data to CSV format
- 🛡️ Comprehensive error handling for network issues and missing elements
- 📊 Removes duplicate entries
- 🎯 Extracts repository names and URLs

## Prerequisites

Before running the scraper, you need to install the following:

### 1. Python 3

Make sure you have Python 3.7 or higher installed. Check your Python version:

```bash
python3 --version
```

If you don't have Python 3 installed, download it from [python.org](https://www.python.org/downloads/).

### 2. Chrome/Chromium Browser

The scraper uses Chrome/Chromium browser. Install it if you haven't already:

**Ubuntu/Debian:**
```bash
sudo apt update
sudo apt install chromium-browser
```

**macOS:**
```bash
brew install --cask google-chrome
```

**Windows:**
Download and install from [google.com/chrome](https://www.google.com/chrome/)

### 3. ChromeDriver

ChromeDriver is required for Selenium to control Chrome browser.

#### Option A: Automatic Installation (Recommended)

The latest versions of Selenium (4.6+) can automatically download and manage ChromeDriver. This is the easiest method.

#### Option B: Manual Installation

If automatic installation doesn't work, install ChromeDriver manually:

1. **Check your Chrome version:**
   - Open Chrome browser
   - Go to `chrome://version/`
   - Note the version number (e.g., 120.0.6099.109)

2. **Download matching ChromeDriver:**
   - Visit [ChromeDriver Downloads](https://googlechromelabs.github.io/chrome-for-testing/)
   - Download the version that matches your Chrome version
   - Choose the appropriate version for your operating system

3. **Install ChromeDriver:**

   **Ubuntu/Debian:**
   ```bash
   unzip chromedriver_linux64.zip
   sudo mv chromedriver /usr/local/bin/
   sudo chmod +x /usr/local/bin/chromedriver
   ```

   **macOS:**
   ```bash
   unzip chromedriver_mac64.zip
   sudo mv chromedriver /usr/local/bin/
   sudo chmod +x /usr/local/bin/chromedriver
   ```

   **Windows:**
   - Extract the `chromedriver.exe` file
   - Add the directory containing `chromedriver.exe` to your PATH environment variable

4. **Verify installation:**
   ```bash
   chromedriver --version
   ```

## Installation

1. **Clone the repository (if not already done):**
   ```bash
   git clone https://github.com/alibaba/nacos.git
   cd nacos
   ```

2. **Install Python dependencies:**
   ```bash
   pip3 install -r requirements.txt
   ```

   Or install Selenium directly:
   ```bash
   pip3 install selenium
   ```

## Usage

### Basic Usage

Run the scraper with default settings (headless mode):

```bash
python3 nacos_dependents_scraper.py
```

### Advanced Usage

If you want to modify the scraper behavior, edit the `nacos_dependents_scraper.py` file:

**Run with visible browser (for debugging):**

Change line in the `main()` function:
```python
scraper = NacosDependentsScraper(headless=False)  # Set to False to see the browser
```

**Adjust timeout settings:**

Modify class constants in the `NacosDependentsScraper` class:
```python
PAGE_LOAD_TIMEOUT = 30      # Page load timeout in seconds
ELEMENT_WAIT_TIMEOUT = 10   # Element wait timeout in seconds
SCROLL_PAUSE_TIME = 2       # Pause between page actions in seconds
```

## Output

The script generates a CSV file named `nacos_dependents.csv` in the current directory with the following columns:

- **Repository Name**: The name of the dependent repository (e.g., `username/repo-name`)
- **Repository URL**: The full GitHub URL of the repository

Example output:
```csv
Repository Name,Repository URL
example-user/example-repo,https://github.com/example-user/example-repo
another-user/another-repo,https://github.com/another-user/another-repo
```

## Troubleshooting

### Common Issues and Solutions

#### 1. `WebDriverException: chrome not reachable`

**Solution:**
- Ensure Chrome/Chromium is installed
- Try running with `headless=False` to see if there are browser-specific issues
- Update Chrome to the latest version

#### 2. `SessionNotCreatedException: session not created: This version of ChromeDriver only supports Chrome version X`

**Solution:**
- Update ChromeDriver to match your Chrome version
- Or update Chrome to match your ChromeDriver version
- ChromeDriver and Chrome versions must be compatible

#### 3. `TimeoutException: Message: timeout waiting for page to load`

**Solution:**
- Check your internet connection
- Increase `PAGE_LOAD_TIMEOUT` value in the script
- GitHub might be experiencing issues; try again later

#### 4. `No such element` errors

**Solution:**
- GitHub's page structure might have changed
- Check if the page loads correctly in a regular browser
- The script might need updates to match new HTML structure

#### 5. `Permission denied` when writing CSV file

**Solution:**
- Ensure you have write permissions in the current directory
- Try running the script from a different directory
- On Linux/Mac, check file permissions with `ls -la`

#### 6. Rate limiting or bot detection

**Solution:**
- Increase `SCROLL_PAUSE_TIME` to add more delays between requests
- Run the script during off-peak hours
- GitHub might temporarily block automated requests

### Getting Help

If you encounter issues not covered here:

1. Check the console output for specific error messages
2. Run with `headless=False` to visually debug
3. Verify all prerequisites are correctly installed
4. Check GitHub status page: [githubstatus.com](https://www.githubstatus.com/)

## Script Features and Implementation Details

### Error Handling

The script includes comprehensive error handling for:

- **Network timeouts**: Configurable timeout values for page loads and element waits
- **Missing elements**: Graceful handling when expected elements are not found
- **Page load issues**: Retry logic and informative error messages
- **Stale element references**: Handles dynamic page content
- **Keyboard interrupts**: Clean shutdown on Ctrl+C

### Pagination

The script automatically:
- Detects pagination links on the GitHub dependents page
- Navigates through all available pages
- Stops when no more pages are available
- Provides progress information for each page

### Data Extraction

The script:
- Uses CSS selectors to identify repository links
- Extracts both repository names and URLs
- Validates data before storing
- Removes duplicate entries based on repository URL

### Performance Optimization

- Configurable wait times to balance speed and reliability
- Headless mode for faster execution
- Efficient duplicate removal
- Batch processing of pages

## Example Run

```
============================================================
Nacos GitHub Dependents Scraper
============================================================
✓ WebDriver initialized successfully
Navigating to https://github.com/alibaba/nacos/network/dependents...
✓ Successfully loaded dependents page

=== Starting to scrape Nacos dependents ===

--- Processing page 1 ---
Found 30 repository links on current page
✓ Extracted 30 dependents from current page
Total dependents collected so far: 30
Next page available, navigating...
✓ Navigated to next page

--- Processing page 2 ---
Found 30 repository links on current page
✓ Extracted 30 dependents from current page
Total dependents collected so far: 60
...

✓ No more pages available

✓ Scraping completed! Total unique dependents: 150

Saving data to nacos_dependents.csv...
✓ Successfully saved 150 dependents to nacos_dependents.csv

✓ Browser closed successfully

============================================================
✓ Scraping completed successfully!
============================================================
```

## Notes

- The script respects GitHub's page structure and rate limits
- Scraping large numbers of dependents may take several minutes
- The output CSV file will be overwritten on each run
- Make sure you have sufficient disk space for the output file

## License

This script is part of the Nacos project and follows the same license as the main project (Apache License 2.0).
