#!/usr/bin/env python3
"""
Nacos GitHub Dependents Scraper

This script scrapes the list of repositories dependent on alibaba/nacos
from its GitHub dependencies page and exports them to a CSV file.

Requirements:
    - Python 3.x
    - Selenium
    - Chrome/Chromium browser
    - ChromeDriver (compatible with your Chrome version)

Usage:
    python nacos_dependents_scraper.py

Output:
    nacos_dependents.csv - CSV file with columns: Repository Name, Repository URL
"""

import csv
import time
import sys
from typing import List, Dict, Optional
from urllib.parse import urlparse
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.common.exceptions import (
    TimeoutException,
    NoSuchElementException,
    WebDriverException,
    StaleElementReferenceException
)
from selenium.webdriver.chrome.options import Options


class NacosDependentsScraper:
    """Scraper for extracting GitHub dependents of alibaba/nacos repository."""
    
    # GitHub dependents page URL for alibaba/nacos
    BASE_URL = "https://github.com/alibaba/nacos/network/dependents"
    
    # Output CSV file name
    OUTPUT_FILE = "nacos_dependents.csv"
    
    # Timeout settings (in seconds)
    PAGE_LOAD_TIMEOUT = 30
    ELEMENT_WAIT_TIMEOUT = 10
    SCROLL_PAUSE_TIME = 2
    
    def __init__(self, headless: bool = True):
        """
        Initialize the scraper with a Selenium WebDriver.
        
        Args:
            headless (bool): Run browser in headless mode (default: True)
        """
        self.headless = headless
        self.driver: Optional[webdriver.Chrome] = None
        self.dependents: List[Dict[str, str]] = []
        
    def setup_driver(self) -> None:
        """
        Set up and configure the Chrome WebDriver.
        
        Raises:
            WebDriverException: If driver setup fails
        """
        try:
            chrome_options = Options()
            
            # Configure browser options
            if self.headless:
                chrome_options.add_argument('--headless')
            
            chrome_options.add_argument('--no-sandbox')
            chrome_options.add_argument('--disable-dev-shm-usage')
            chrome_options.add_argument('--disable-gpu')
            chrome_options.add_argument('--window-size=1920,1080')
            
            # Add user agent to avoid bot detection
            chrome_options.add_argument(
                'user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) '
                'AppleWebKit/537.36 (KHTML, like Gecko) '
                'Chrome/120.0.0.0 Safari/537.36'
            )
            
            # Initialize the driver
            self.driver = webdriver.Chrome(options=chrome_options)
            self.driver.set_page_load_timeout(self.PAGE_LOAD_TIMEOUT)
            
            print("✓ WebDriver initialized successfully")
            
        except WebDriverException as e:
            print(f"✗ Error setting up WebDriver: {e}", file=sys.stderr)
            print("\nPlease ensure:")
            print("1. Chrome/Chromium browser is installed")
            print("2. ChromeDriver is installed and in PATH")
            print("3. ChromeDriver version matches your Chrome version")
            raise
    
    def navigate_to_dependents_page(self) -> bool:
        """
        Navigate to the GitHub dependents page.
        
        Returns:
            bool: True if navigation successful, False otherwise
        """
        try:
            print(f"Navigating to {self.BASE_URL}...")
            self.driver.get(self.BASE_URL)
            
            # Wait for the page to load by checking for dependents container
            WebDriverWait(self.driver, self.ELEMENT_WAIT_TIMEOUT).until(
                EC.presence_of_element_located((By.ID, "dependents"))
            )
            
            print("✓ Successfully loaded dependents page")
            time.sleep(self.SCROLL_PAUSE_TIME)
            return True
            
        except TimeoutException:
            print("✗ Timeout waiting for page to load", file=sys.stderr)
            return False
        except WebDriverException as e:
            print(f"✗ Error navigating to page: {e}", file=sys.stderr)
            return False
    
    def extract_dependents_from_page(self) -> List[Dict[str, str]]:
        """
        Extract dependent repository information from the current page.
        
        Returns:
            List[Dict[str, str]]: List of dictionaries containing repository name and URL
        """
        dependents_on_page = []
        
        try:
            # Wait for repository links to be present
            WebDriverWait(self.driver, self.ELEMENT_WAIT_TIMEOUT).until(
                EC.presence_of_element_located(
                    (By.CSS_SELECTOR, "div#dependents a[data-hovercard-type='repository']")
                )
            )
            
            # Find all repository links on the page
            # GitHub uses specific attributes for repository links
            repo_links = self.driver.find_elements(
                By.CSS_SELECTOR, 
                "div#dependents a[data-hovercard-type='repository']"
            )
            
            print(f"Found {len(repo_links)} repository links on current page")
            
            for link in repo_links:
                try:
                    # Extract repository name and URL
                    repo_url = link.get_attribute('href')
                    repo_name = link.text.strip()
                    
                    # Validate extracted data with proper URL parsing
                    if repo_url and repo_name:
                        parsed_url = urlparse(repo_url)
                        # Ensure the URL is from GitHub (check netloc/domain)
                        if parsed_url.netloc == 'github.com' or parsed_url.netloc.endswith('.github.com'):
                            dependents_on_page.append({
                                'name': repo_name,
                                'url': repo_url
                            })
                        
                except StaleElementReferenceException:
                    # Element reference became stale, skip this element
                    continue
                except Exception as e:
                    print(f"Warning: Error extracting repository data: {e}")
                    continue
            
            print(f"✓ Extracted {len(dependents_on_page)} dependents from current page")
            
        except TimeoutException:
            print("✗ Timeout waiting for repository elements", file=sys.stderr)
        except NoSuchElementException:
            print("✗ Could not find repository elements on page", file=sys.stderr)
        except Exception as e:
            print(f"✗ Unexpected error extracting dependents: {e}", file=sys.stderr)
        
        return dependents_on_page
    
    def has_next_page(self) -> bool:
        """
        Check if there is a next page of dependents.
        
        Returns:
            bool: True if next page exists, False otherwise
        """
        try:
            # GitHub uses pagination with "Next" button
            # Look for the pagination container
            pagination_links = self.driver.find_elements(
                By.CSS_SELECTOR, 
                "div.paginate-container a"
            )
            
            for link in pagination_links:
                # Check if this is a "Next" link and it's enabled
                link_rel = link.get_attribute('rel') or ''
                if 'Next' in link.text or 'next' in link_rel:
                    # Check if the link is not disabled
                    parent_element = link.find_element(By.XPATH, '..')
                    parent_class = parent_element.get_attribute('class') or ''
                    if 'disabled' not in parent_class:
                        return True
            
            return False
            
        except NoSuchElementException:
            return False
        except Exception as e:
            print(f"Warning: Error checking for next page: {e}")
            return False
    
    def navigate_to_next_page(self) -> bool:
        """
        Navigate to the next page of dependents.
        
        Returns:
            bool: True if navigation successful, False otherwise
        """
        try:
            # Find and click the "Next" button
            pagination_links = self.driver.find_elements(
                By.CSS_SELECTOR, 
                "div.paginate-container a"
            )
            
            for link in pagination_links:
                link_rel = link.get_attribute('rel') or ''
                if 'Next' in link.text or 'next' in link_rel:
                    parent_element = link.find_element(By.XPATH, '..')
                    parent_class = parent_element.get_attribute('class') or ''
                    if 'disabled' not in parent_class:
                        link.click()
                        
                        # Wait for new page to load
                        time.sleep(self.SCROLL_PAUSE_TIME)
                        
                        # Wait for dependents container to refresh
                        WebDriverWait(self.driver, self.ELEMENT_WAIT_TIMEOUT).until(
                            EC.presence_of_element_located((By.ID, "dependents"))
                        )
                        
                        print("✓ Navigated to next page")
                        return True
            
            return False
            
        except TimeoutException:
            print("✗ Timeout navigating to next page", file=sys.stderr)
            return False
        except Exception as e:
            print(f"✗ Error navigating to next page: {e}", file=sys.stderr)
            return False
    
    def scrape_all_dependents(self) -> None:
        """
        Scrape all dependents across all pages.
        """
        print("\n=== Starting to scrape Nacos dependents ===\n")
        
        page_number = 1
        
        while True:
            print(f"\n--- Processing page {page_number} ---")
            
            # Extract dependents from current page
            dependents_on_page = self.extract_dependents_from_page()
            self.dependents.extend(dependents_on_page)
            
            print(f"Total dependents collected so far: {len(self.dependents)}")
            
            # Check if there's a next page
            if self.has_next_page():
                print("Next page available, navigating...")
                if not self.navigate_to_next_page():
                    print("Failed to navigate to next page, stopping pagination")
                    break
                page_number += 1
            else:
                print("\n✓ No more pages available")
                break
        
        # Remove duplicates based on repository URL
        original_count = len(self.dependents)
        seen_urls = set()
        unique_dependents = []
        
        for dep in self.dependents:
            if dep['url'] not in seen_urls:
                seen_urls.add(dep['url'])
                unique_dependents.append(dep)
        
        self.dependents = unique_dependents
        
        if original_count != len(self.dependents):
            print(f"\nRemoved {original_count - len(self.dependents)} duplicate entries")
        
        print(f"\n✓ Scraping completed! Total unique dependents: {len(self.dependents)}")
    
    def save_to_csv(self) -> None:
        """
        Save the collected dependents data to a CSV file.
        
        Raises:
            IOError: If file writing fails
        """
        try:
            if not self.dependents:
                print("⚠ No dependents to save", file=sys.stderr)
                return
            
            print(f"\nSaving data to {self.OUTPUT_FILE}...")
            
            with open(self.OUTPUT_FILE, 'w', newline='', encoding='utf-8') as csvfile:
                fieldnames = ['Repository Name', 'Repository URL']
                writer = csv.DictWriter(csvfile, fieldnames=fieldnames)
                
                # Write header
                writer.writeheader()
                
                # Write data rows
                for dep in self.dependents:
                    writer.writerow({
                        'Repository Name': dep['name'],
                        'Repository URL': dep['url']
                    })
            
            print(f"✓ Successfully saved {len(self.dependents)} dependents to {self.OUTPUT_FILE}")
            
        except IOError as e:
            print(f"✗ Error writing to CSV file: {e}", file=sys.stderr)
            raise
        except Exception as e:
            print(f"✗ Unexpected error saving to CSV: {e}", file=sys.stderr)
            raise
    
    def cleanup(self) -> None:
        """Clean up resources and close the browser."""
        if self.driver:
            try:
                self.driver.quit()
                print("\n✓ Browser closed successfully")
            except Exception as e:
                print(f"Warning: Error closing browser: {e}")
    
    def run(self) -> bool:
        """
        Run the complete scraping workflow.
        
        Returns:
            bool: True if scraping completed successfully, False otherwise
        """
        try:
            # Setup the WebDriver
            self.setup_driver()
            
            # Navigate to the dependents page
            if not self.navigate_to_dependents_page():
                return False
            
            # Scrape all dependents
            self.scrape_all_dependents()
            
            # Save to CSV
            self.save_to_csv()
            
            return True
            
        except KeyboardInterrupt:
            print("\n\n⚠ Scraping interrupted by user")
            return False
        except Exception as e:
            print(f"\n✗ Fatal error during scraping: {e}", file=sys.stderr)
            return False
        finally:
            # Always clean up
            self.cleanup()


def main():
    """Main entry point for the script."""
    print("=" * 60)
    print("Nacos GitHub Dependents Scraper")
    print("=" * 60)
    
    # Create and run the scraper
    # Set headless=False to see the browser in action (useful for debugging)
    scraper = NacosDependentsScraper(headless=True)
    
    success = scraper.run()
    
    if success:
        print("\n" + "=" * 60)
        print("✓ Scraping completed successfully!")
        print("=" * 60)
        sys.exit(0)
    else:
        print("\n" + "=" * 60)
        print("✗ Scraping failed")
        print("=" * 60)
        sys.exit(1)


if __name__ == "__main__":
    main()
